package com.primandproper.platform.circuitbreaking.partitioned

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitBreakerConfig
import com.primandproper.platform.circuitbreaking.realCircuitBreaker
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.ObserverFactory

/** Partition attribute value for the shared fallback breaker; mirrors Go's `globalPartition`. */
internal const val GLOBAL_PARTITION: String = "global"

/**
 * Configures a partitioned (keyed) circuit breaker — the analog of platform-go's
 * `partitionedcfg.Config`. [keys] enumerates the partitions that get a dedicated breaker; every
 * dedicated breaker plus the shared global one is built from the same [base] config.
 */
public class KeyedCircuitBreakerConfig {
    /** Partitions (e.g. tenant IDs) that each receive their own breaker. */
    public val keys: MutableList<String> = mutableListOf()

    /** The template config every dedicated breaker and the global fallback are built from. */
    public val base: CircuitBreakerConfig = CircuitBreakerConfig()

    /** Configures [base] in place: `base { failureThreshold = 5 }`. */
    public fun base(configure: CircuitBreakerConfig.() -> Unit) {
        base.configure()
    }

    /** Fills unset [base] fields with their defaults; the analog of Go's `Config.EnsureDefaults`. */
    public fun ensureDefaults() {
        base.ensureDefaults()
    }

    /** Validates [base] and every key, throwing [IllegalArgumentException] on the first violation. */
    public fun validate() {
        base.validate()
        keys.forEach { require(it.isNotBlank()) { "circuitbreaking: partition keys must be non-blank" } }
    }
}

/**
 * Builds a [KeyedCircuitBreaker] from [config] — the analog of `Config.ProvideKeyedCircuitBreaker`.
 * The base config is copied and defaulted, then validated; an invalid config degrades to
 * [NoopKeyedCircuitBreaker] (logging the reason) exactly like the Go provider. Each breaker is tagged
 * with its partition (`"global"` for the fallback) for log correlation and as the seam where a
 * per-partition metric attribute reattaches once metrics land.
 *
 * [observerFactory] hands out a per-breaker [com.primandproper.platform.observability.Observer]; it
 * degrades to noop observers when absent.
 */
public fun KeyedCircuitBreaker(
    config: KeyedCircuitBreakerConfig,
    observerFactory: ObserverFactory? = null,
): KeyedCircuitBreaker {
    val base = config.base.copy().apply { ensureDefaults() }
    return try {
        base.validate()
        config.keys.forEach {
            require(it.isNotBlank()) { "circuitbreaking: partition keys must be non-blank, got \"$it\"" }
        }

        val global = realCircuitBreaker(base, observerFactory?.named(base.name), GLOBAL_PARTITION)
        val breakers: Map<String, CircuitBreaker> =
            config.keys.associateWith { key ->
                realCircuitBreaker(base, observerFactory?.named(base.name), key)
            }

        DefaultKeyedCircuitBreaker(global, breakers)
    } catch (e: IllegalArgumentException) {
        (observerFactory?.logger ?: NoopLogger)
            .error("invalid keyed circuit breaker config, providing noop keyed circuit breaker", e)
        NoopKeyedCircuitBreaker
    }
}
