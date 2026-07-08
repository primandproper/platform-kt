package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.errors.PlatformException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sentinel thrown when a breaker rejects a call because it is open (or exhausting its half-open
 * probe budget). platform-go declares this as `circuitbreaking.ErrCircuitBroken`.
 *
 * The single instance is *owned by* `:errors` ([com.primandproper.platform.errors.ErrCircuitBroken])
 * because that module's HTTP/gRPC mappers match it by identity, and `:circuitbreaking` depends on
 * `:errors` (owning it here would create a dependency cycle). We re-export the exact same instance,
 * so `isError(e, ErrCircuitBroken)` behaves like Go's `errors.Is(err, ErrCircuitBroken)` and the
 * `:errors` mappers still resolve it to HTTP 503 / the right gRPC code.
 */
public val ErrCircuitBroken: PlatformException = com.primandproper.platform.errors.ErrCircuitBroken

/**
 * The three states of a breaker, mirroring the classic circuit-breaker state machine.
 *
 * platform-go wraps `rubyist/circuitbreaker`, which trips on a rolling error *rate* and resets via
 * exponential backoff. This port models the equivalent lifecycle explicitly as a [StateFlow] so
 * Android consumers can observe transitions (drive UI, gate retries) — see [CircuitBreaker.state].
 */
public enum class CircuitState {
    /** Calls pass through; consecutive failures are counted toward the trip threshold. */
    CLOSED,

    /** Calls are rejected with [ErrCircuitBroken] until the reset timeout elapses. */
    OPEN,

    /** A limited number of probe calls are admitted to test whether the dependency recovered. */
    HALF_OPEN,
}

/**
 * Tracks failures and successes to decide whether an operation should proceed — the coroutine-native
 * analog of platform-go's `circuitbreaking.CircuitBreaker`.
 *
 * Go exposes the primitive quartet `Failed()`/`Succeeded()`/`CanProceed()`/`CannotProceed()`, leaving
 * call sites to wire the "check, run, report" dance by hand. This port folds that into a single
 * [execute]: it admits or rejects the call, runs [block], and records the outcome, so the breaker
 * can't be left in an inconsistent state by a caller that forgot to report. The live [state] is
 * published as a [StateFlow] for observation.
 */
public interface CircuitBreaker {
    /** The current breaker state, observable for transitions. */
    public val state: StateFlow<CircuitState>

    /**
     * Runs [block] under this breaker and returns its result. Throws [ErrCircuitBroken] without
     * invoking [block] when the breaker is open (or has no probe budget left in half-open);
     * otherwise runs [block], counting a thrown exception as a failure and a normal return as a
     * success. [kotlinx.coroutines.CancellationException] propagates without being counted either
     * way, so a cancelled coroutine never trips the breaker.
     */
    public suspend fun <T> execute(block: suspend () -> T): T
}

/** Runs [block] under [breaker]. A thin top-level wrapper so call sites read as `circuitBreak(cb) { }`. */
public suspend fun <T> circuitBreak(
    breaker: CircuitBreaker,
    block: suspend () -> T,
): T = breaker.execute(block)

/**
 * Returns [breaker] if non-null, otherwise the always-closed [NoopCircuitBreaker]. Mirrors
 * platform-go's `EnsureCircuitBreaker`, so call sites never branch on nil.
 */
public fun ensureCircuitBreaker(breaker: CircuitBreaker?): CircuitBreaker = breaker ?: NoopCircuitBreaker

/**
 * A breaker that always allows operations to proceed and never trips — the analog of platform-go's
 * `circuitbreaking/noop`. Used as the safe default and wherever protection should be a no-op.
 */
public object NoopCircuitBreaker : CircuitBreaker {
    private val _state = MutableStateFlow(CircuitState.CLOSED)
    override val state: StateFlow<CircuitState> = _state.asStateFlow()

    override suspend fun <T> execute(block: suspend () -> T): T = block()
}
