package com.primandproper.platform.healthcheck

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bounds each individual health check so one slow or hung component can't stall the whole probe.
 * Port of platform-go's `defaultCheckTimeout`. Where Go wraps every check's `context.Context` in a
 * `context.WithTimeout`, this port runs each check under [withTimeout]; a check that overruns is
 * cancelled at its next suspension point and reported [Status.DOWN].
 */
internal val DEFAULT_CHECK_TIMEOUT: Duration = 5.seconds

/**
 * Component health status. Port of platform-go's `healthcheck.Status`, a `string` enum whose [value]
 * is the wire form ("up" / "down") emitted when a [Result] is serialized for an actuator-style probe.
 */
public enum class Status(
    public val value: String,
) {
    UP("up"),
    DOWN("down"),
}

/**
 * The result of a single component check. Port of platform-go's `ComponentResult`; [message] carries
 * the failure detail on a [Status.DOWN] result and is empty (Go's `omitempty`) when the component is
 * [Status.UP].
 */
public data class ComponentResult(
    val status: Status,
    val message: String = "",
)

/**
 * The aggregate result of running every registered check. Port of platform-go's `Result`. [status] is
 * [Status.DOWN] if any component is down and [Status.UP] otherwise; [components] maps each checker's
 * name to its outcome.
 */
public data class Result(
    val components: Map<String, ComponentResult>,
    val status: Status,
)

/**
 * Performs a health check for a single component. Port of platform-go's `Checker`.
 *
 * Go's `Check(ctx) error` becomes a [check] that suspends and signals failure by throwing (the
 * idiomatic Kotlin translation of an `error` return); [name] replaces Go's `Name()` method. A check
 * must honor cancellation so the per-check deadline in [Registry.checkAll] can bound it.
 */
public interface Checker {
    /** The component's name, used as its key in [Result.components]. */
    public val name: String

    /** Runs the check, throwing if the component is unhealthy. */
    public suspend fun check()
}

/**
 * Holds checkers and runs them. Port of platform-go's `Registry`.
 *
 * TODO(server): platform-go exposes a registry through an actuator-style HTTP probe endpoint. That
 *   wiring — translating a [Result] into a JSON body and a 200/503 status — belongs to the server
 *   module (`:server-ktor`), not here; this module only owns the registry and its aggregation.
 */
public interface Registry {
    /** Adds [checker] to the registry. A `null` checker is ignored, matching Go's nil guard. */
    public fun register(checker: Checker?)

    /** Runs every registered checker under a per-check deadline and returns the aggregate [Result]. */
    public suspend fun checkAll(): Result
}

/**
 * Returns a new, empty [Registry]. Port of platform-go's `NewRegistry`.
 *
 * @param logger optional logger; defaults to noop. When supplied, a component's transitions between
 *   healthy and unhealthy are logged so a flapping dependency is visible instead of being silently
 *   folded into the aggregate [Result].
 */
public fun Registry(logger: Logger = NoopLogger): Registry = DefaultRegistry(logger)

internal class DefaultRegistry(
    private val logger: Logger,
) : Registry {
    // Go guards its checker slice with a sync.RWMutex; registration happens off the coroutine world
    // (typically at wiring time), so a plain monitor lock is the faithful, non-suspending analog.
    private val lock = Any()
    private val checkers = mutableListOf<Checker>()

    // Last observed status per component, so checkAll can log only the failing/recovering transitions
    // (not every probe) — the signal that makes a flapping component visible. Guarded by [lock].
    private val lastStatus = mutableMapOf<String, Status>()

    override fun register(checker: Checker?) {
        if (checker == null) return
        synchronized(lock) { checkers.add(checker) }
    }

    override suspend fun checkAll(): Result =
        coroutineScope {
            // Snapshot under the lock so a concurrent register() can't mutate the slice mid-run,
            // mirroring Go's copy-under-RLock.
            val snapshot = synchronized(lock) { checkers.toList() }

            // Run the checks concurrently, each under its own timeout, so a single slow check bounds
            // only itself instead of serially stalling the probe.
            val outcomes =
                snapshot
                    .map { checker -> async { checker.name to runChecked(checker) } }
                    .awaitAll()

            val components = LinkedHashMap<String, ComponentResult>(outcomes.size)
            var status = Status.UP
            for ((name, result) in outcomes) {
                components[name] = result
                logTransition(name, result)
                if (result.status == Status.DOWN) {
                    status = Status.DOWN
                }
            }

            Result(components = components, status = status)
        }

    /** Logs a component only when it crosses between healthy and unhealthy, so flapping is visible. */
    private fun logTransition(
        name: String,
        result: ComponentResult,
    ) {
        val previous = synchronized(lock) { lastStatus.put(name, result.status) }
        when {
            result.status == Status.DOWN && previous != Status.DOWN ->
                logger.withValue("component", name).withValue("reason", result.message).warn("health check failed")
            result.status == Status.UP && previous == Status.DOWN ->
                logger.withValue("component", name).info("health check recovered")
        }
    }

    private suspend fun runChecked(checker: Checker): ComponentResult =
        try {
            withTimeout(DEFAULT_CHECK_TIMEOUT) { checker.check() }
            ComponentResult(Status.UP)
        } catch (timeout: TimeoutCancellationException) {
            // Only OUR per-check withTimeout should count as DOWN. If the *enclosing* scope is itself
            // being cancelled (an ancestor withTimeout, or a cancelled probe), this cancellation isn't
            // the check's own deadline — rethrow it instead of misreporting the component as down.
            currentCoroutineContext().ensureActive()
            ComponentResult(Status.DOWN, timeout.message ?: "health check timed out")
        } catch (cancellation: CancellationException) {
            // A cancellation of the enclosing scope is not a check failure; let it propagate.
            throw cancellation
        } catch (failure: Throwable) {
            ComponentResult(Status.DOWN, failure.message ?: failure.toString())
        }
}
