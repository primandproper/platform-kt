package com.primandproper.platform.circuitbreaking.partitioned

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitBreakerConfig
import com.primandproper.platform.circuitbreaking.realCircuitBreaker
import com.primandproper.platform.observability.ObserverFactory

/** Partition attribute value for the shared fallback breaker; mirrors Go's `globalPartition`. */
internal const val GLOBAL_PARTITION: String = "global"

/**
 * Configures a partitioned (keyed) circuit breaker — the analog of platform-go's
 * `partitionedcfg.Config`. An immutable data class: [keys] enumerates the partitions that get a
 * dedicated breaker; every dedicated breaker plus the shared global one is built from the same [base]
 * config. [base] is already defaulted and validated by its own construction; the per-key rule is
 * enforced in [init].
 *
 * @param base the template config every dedicated breaker and the global fallback are built from.
 * @param keys partitions (e.g. tenant IDs) that each receive their own breaker; each must be non-blank.
 */
public data class KeyedCircuitBreakerConfig(
    val base: CircuitBreakerConfig,
    val keys: List<String> = emptyList(),
) {
    init {
        keys.forEach { require(it.isNotBlank()) { "circuitbreaking: partition keys must be non-blank" } }
    }
}

/**
 * Builds a [KeyedCircuitBreaker] from [config] — the analog of `Config.ProvideKeyedCircuitBreaker`.
 * [config] (its [base][KeyedCircuitBreakerConfig.base] and every partition key) is already defaulted
 * and validated at its own construction; an invalid config (or a blank partition key) **throws**
 * [IllegalArgumentException] there rather than silently degrading to a noop, honoring the README's
 * "misconfiguration fails loudly at startup" contract. A caller that genuinely wants no protection
 * opts in explicitly via [NoopKeyedCircuitBreaker]. Each breaker is tagged with its partition
 * (`"global"` for the fallback) for log correlation and as the seam where a per-partition metric
 * attribute reattaches once metrics land.
 *
 * [observerFactory] hands out a per-breaker [com.primandproper.platform.observability.Observer]; it
 * degrades to noop observers when absent.
 */
public fun KeyedCircuitBreaker(
    config: KeyedCircuitBreakerConfig,
    observerFactory: ObserverFactory? = null,
): KeyedCircuitBreaker {
    val base = config.base

    val global = realCircuitBreaker(base, observerFactory?.named(base.name), GLOBAL_PARTITION)
    val breakers: Map<String, CircuitBreaker> =
        config.keys.associateWith { key ->
            realCircuitBreaker(base, observerFactory?.named(base.name), key)
        }

    return DefaultKeyedCircuitBreaker(global, breakers)
}
