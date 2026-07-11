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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Builds a [CircuitBreaker] from [config] — the analog of `Config.ProvideCircuitBreaker`. [config] is
 * an immutable value already defaulted and validated at its own construction (an *invalid* config
 * **throws** [IllegalArgumentException] there rather than silently degrading to a noop, honoring the
 * README's "misconfiguration fails loudly at startup, not silently at first use" contract, mirroring
 * `SecretsConfig.SecretSource`). Build one with named arguments, e.g.
 * `CircuitBreaker(CircuitBreakerConfig(name = "svc", failureThreshold = 5))`. A caller that genuinely
 * wants no protection opts in explicitly via [NoopCircuitBreaker].
 *
 * [observer] logs state transitions and degrades to a noop observer when absent, mirroring how the
 * rest of this port binds to `observability-api`.
 */
public fun CircuitBreaker(
    config: CircuitBreakerConfig,
    observer: Observer = noopObserver(config.name),
): CircuitBreaker = RealCircuitBreaker(config, observer, partition = null, timeSource = TimeSource.Monotonic)

/**
 * Internal shared constructor used by both the public factory and the partitioned builder. [partition]
 * is logged (and is the seam where a per-partition metric attribute reattaches once metrics land);
 * [config] is assumed already defaulted and validated.
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

    // Guarded by [mutex]. Rate-limits the rejection log so a storm of rejections in a single open
    // window leaves a signal without flooding the logs.
    private var rejectionsSinceLog: Long = 0
    private var lastRejectionLogMark: TimeMark? = null

    /** How a call was admitted, so the outcome is accounted against the right state. */
    private enum class Admission { NORMAL, PROBE }

    override suspend fun <T> execute(block: suspend () -> T): T {
        val admission = admit() // throws CircuitBrokenException when the call is rejected
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
                        reject(CircuitState.OPEN)
                    }
                }

                CircuitState.HALF_OPEN -> {
                    if (probesInFlight < config.halfOpenMaxProbes) {
                        probesInFlight++
                        Admission.PROBE
                    } else {
                        // Probe budget exhausted; keep rejecting until an in-flight probe resolves.
                        reject(CircuitState.HALF_OPEN)
                    }
                }
            }
        }

    /**
     * Records a rejection and throws [CircuitBrokenException]. Emits a rate-limited warning — at most
     * one line per [REJECTION_LOG_INTERVAL], carrying the count suppressed since the last line — so a
     * flood of rejections in an open window leaves a signal (state transitions log, but individual
     * rejections otherwise would not) without drowning the logs. Called under [mutex], so the counters
     * need no extra synchronization and the check stays cheap on the hot path. The breaker
     * name/partition ride along via [log]'s bound context.
     */
    private fun reject(rejectedIn: CircuitState): Nothing {
        rejectionsSinceLog++
        val since = lastRejectionLogMark
        if (since == null || since.elapsedNow() >= REJECTION_LOG_INTERVAL) {
            log
                .withValue("state", rejectedIn.name)
                .withValue("rejections", rejectionsSinceLog)
                .warn("circuit breaker rejecting calls")
            rejectionsSinceLog = 0
            lastRejectionLogMark = timeSource.markNow()
        }
        throw CircuitBrokenException()
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

    private companion object {
        /** Minimum interval between rejection log lines, so a rejection storm can't flood the logs. */
        private val REJECTION_LOG_INTERVAL: Duration = 5.seconds
    }
}
