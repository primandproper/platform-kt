package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Builds a [CircuitBreaker] from [config] — the analog of `Config.ProvideCircuitBreaker`. The config
 * is copied and defaulted at construction ([CircuitBreakerConfig.ensureDefaults]), then validated;
 * an *invalid* config degrades to [NoopCircuitBreaker] (logging the reason) exactly like the Go
 * provider, which returns a noop rather than failing the whole wiring.
 *
 * [observer] logs state transitions and degrades to a noop observer when absent, mirroring how the
 * rest of this port binds to `observability-api`.
 */
public fun CircuitBreaker(
    config: CircuitBreakerConfig,
    observer: Observer? = null,
): CircuitBreaker {
    val normalized = config.copy().apply { ensureDefaults() }
    val obs = observer ?: noopObserver(normalized.name)
    return try {
        normalized.validate()
        RealCircuitBreaker(normalized, obs, partition = null, timeSource = TimeSource.Monotonic)
    } catch (e: IllegalArgumentException) {
        obs.logger.error("invalid circuit breaker config, providing noop circuit breaker", e)
        NoopCircuitBreaker
    }
}

/** Builds a [CircuitBreaker] from an inline config block: `CircuitBreaker { failureThreshold = 5 }`. */
public fun CircuitBreaker(
    observer: Observer? = null,
    configure: CircuitBreakerConfig.() -> Unit,
): CircuitBreaker = CircuitBreaker(CircuitBreakerConfig().apply(configure), observer)

/**
 * Internal shared constructor used by both the public factory and the partitioned builder. [partition]
 * is logged (and is the seam where a per-partition metric attribute reattaches once metrics land);
 * [config] is assumed already copied, defaulted, and validated.
 */
internal fun realCircuitBreaker(
    config: CircuitBreakerConfig,
    observer: Observer?,
    partition: String?,
): CircuitBreaker = RealCircuitBreaker(config, observer ?: noopObserver(config.name), partition, TimeSource.Monotonic)

/**
 * Count-based circuit breaker with the classic Closed → Open → HalfOpen lifecycle.
 *
 * All state mutation runs under a [Mutex] so concurrent `execute` calls can't corrupt the counters or
 * publish a torn transition. The open → half-open transition is evaluated lazily when a call arrives
 * (reading [timeSource]) rather than scheduled on a timer, matching how Go's `rubyist` breaker checks
 * its reset deadline inside `Ready()` — no background coroutine to leak.
 */
internal class RealCircuitBreaker(
    private val config: CircuitBreakerConfig,
    observer: Observer,
    partition: String?,
    private val timeSource: TimeSource,
) : CircuitBreaker {
    private val log: Logger =
        observer.logger
            .withValue("circuit_breaker", config.name)
            .let { if (partition != null) it.withValue("partition", partition) else it }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(CircuitState.CLOSED)
    override val state: StateFlow<CircuitState> = _state.asStateFlow()

    // Guarded by [mutex].
    private var consecutiveFailures: Int = 0
    private var probesInFlight: Int = 0
    private var successesInHalfOpen: Int = 0
    private var openMark: TimeMark? = null

    /** How a call was admitted, so the outcome is accounted against the right state. */
    private enum class Admission { NORMAL, PROBE }

    override suspend fun <T> execute(block: suspend () -> T): T {
        val admission = admit() // throws ErrCircuitBroken when the call is rejected
        return try {
            val result = block()
            onSuccess(admission)
            result
        } catch (cancellation: CancellationException) {
            // Cancellation is neither success nor failure: release any reserved probe slot and let it
            // propagate, so a cancelled coroutine never trips or resets the breaker.
            onCancel(admission)
            throw cancellation
        } catch (error: Throwable) {
            onFailure(admission)
            throw error
        }
    }

    private suspend fun admit(): Admission =
        mutex.withLock {
            when (_state.value) {
                CircuitState.CLOSED -> Admission.NORMAL

                CircuitState.OPEN -> {
                    val mark = openMark
                    if (mark != null && mark.elapsedNow() >= config.resetTimeout) {
                        // Reset window elapsed: enter half-open and let this call be the first probe.
                        transitionTo(CircuitState.HALF_OPEN)
                        successesInHalfOpen = 0
                        probesInFlight = 1
                        Admission.PROBE
                    } else {
                        throw ErrCircuitBroken
                    }
                }

                CircuitState.HALF_OPEN -> {
                    if (probesInFlight < config.halfOpenMaxProbes) {
                        probesInFlight++
                        Admission.PROBE
                    } else {
                        // Probe budget exhausted; keep rejecting until an in-flight probe resolves.
                        throw ErrCircuitBroken
                    }
                }
            }
        }

    private suspend fun onSuccess(admission: Admission): Unit =
        mutex.withLock {
            when (admission) {
                Admission.PROBE -> {
                    probesInFlight = (probesInFlight - 1).coerceAtLeast(0)
                    successesInHalfOpen++
                    if (_state.value == CircuitState.HALF_OPEN && successesInHalfOpen >= config.halfOpenMaxProbes) {
                        close()
                    }
                }
                Admission.NORMAL -> {
                    if (_state.value == CircuitState.CLOSED) consecutiveFailures = 0
                }
            }
        }

    private suspend fun onFailure(admission: Admission): Unit =
        mutex.withLock {
            when (admission) {
                Admission.PROBE -> {
                    probesInFlight = (probesInFlight - 1).coerceAtLeast(0)
                    // A failed probe means the dependency is still unhealthy: re-open immediately.
                    if (_state.value == CircuitState.HALF_OPEN) reopen()
                }
                Admission.NORMAL -> {
                    if (_state.value == CircuitState.CLOSED) {
                        consecutiveFailures++
                        if (consecutiveFailures >= config.failureThreshold) trip()
                    }
                }
            }
        }

    private suspend fun onCancel(admission: Admission): Unit =
        mutex.withLock {
            if (admission == Admission.PROBE) probesInFlight = (probesInFlight - 1).coerceAtLeast(0)
        }

    // --- transitions (all callers hold [mutex]) ---

    private fun trip() {
        openMark = timeSource.markNow()
        consecutiveFailures = 0
        probesInFlight = 0
        successesInHalfOpen = 0
        transitionTo(CircuitState.OPEN)
    }

    private fun reopen() {
        openMark = timeSource.markNow()
        probesInFlight = 0
        successesInHalfOpen = 0
        transitionTo(CircuitState.OPEN)
    }

    private fun close() {
        openMark = null
        consecutiveFailures = 0
        probesInFlight = 0
        successesInHalfOpen = 0
        transitionTo(CircuitState.CLOSED)
    }

    private fun transitionTo(to: CircuitState) {
        val from = _state.value
        if (from == to) return
        _state.value = to
        log.withValue("from", from.name).withValue("to", to.name).info("circuit breaker state transition")

        // TODO(metrics): the metrics pillar is deliberately descoped in this port (see
        // docs/PORTING_STATUS.md and observability README "Deliberately descoped"), so transitions are
        // recorded only as the structured log fields above. When the metrics pillar lands, emit the
        // counters platform-go's circuitbreakingcfg emitted here — "<name>_circuit_breaker_tripped"
        // on -> OPEN from CLOSED, "<name>_circuit_breaker_reset" on -> CLOSED, and
        // "<name>_circuit_breaker_failed" per counted failure — tagged with the "partition" attribute.
    }
}
