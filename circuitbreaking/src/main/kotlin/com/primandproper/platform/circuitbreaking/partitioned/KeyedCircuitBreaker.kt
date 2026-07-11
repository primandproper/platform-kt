package com.primandproper.platform.circuitbreaking.partitioned

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker

/**
 * Hands out an independent [CircuitBreaker] per registered key — the analog of platform-go's
 * `circuitbreaking/partitioned.KeyedCircuitBreaker`.
 *
 * Where a plain [CircuitBreaker] shares one state across all traffic, this breaks a heavy or flaky
 * key (a tenant ID, an endpoint) in isolation while other keys keep flowing through a shared global
 * breaker. The set of keyed breakers is fixed at construction and operator-chosen, so per-breaker
 * observability stays low-cardinality.
 */
public interface KeyedCircuitBreaker {
    /** Returns the dedicated breaker registered for [key], or the shared global breaker if [key] was not registered. */
    public fun forKey(key: String): CircuitBreaker

    /** Operator sugar for [forKey], so call sites can read `keyed[tenantId]`. */
    public operator fun get(key: String): CircuitBreaker = forKey(key)
}

/**
 * Builds a [KeyedCircuitBreaker] that serves each key in [breakers] from its dedicated breaker and
 * any other key from [global] — the analog of `partitioned.NewKeyedCircuitBreaker`. [breakers] is
 * copied defensively, so the caller can't mutate the registry afterward.
 */
public fun KeyedCircuitBreaker(
    global: CircuitBreaker,
    breakers: Map<String, CircuitBreaker> = emptyMap(),
): KeyedCircuitBreaker = DefaultKeyedCircuitBreaker(global, breakers)

internal class DefaultKeyedCircuitBreaker(
    private val global: CircuitBreaker,
    breakers: Map<String, CircuitBreaker>,
) : KeyedCircuitBreaker {
    private val breakers: Map<String, CircuitBreaker> = breakers.toMap()

    override fun forKey(key: String): CircuitBreaker = breakers[key] ?: global
}

/**
 * A keyed breaker that serves the always-closed [NoopCircuitBreaker] for every key — the analog of
 * `partitioned/noop`. The same instance is returned for all keys since it carries no state.
 */
public object NoopKeyedCircuitBreaker : KeyedCircuitBreaker {
    override fun forKey(key: String): CircuitBreaker = NoopCircuitBreaker
}
