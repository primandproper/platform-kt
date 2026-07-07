package com.primandproper.platform.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The supported cache providers. Port of platform-go's `config.ProviderMemory` / `config.ProviderRedis`
 * constants. [value] is the wire/string form validated against configuration.
 */
public enum class CacheProvider(
    public val value: String,
) {
    MEMORY("memory"),
    REDIS("redis"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(ProviderMemory, ProviderRedis)`
         * rejecting an unknown provider name.
         */
        public fun fromValue(value: String): CacheProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider-agnostic cache configuration. Port of the portable part of platform-go's `config.Config`:
 * the chosen [provider] and the [expiry] applied by expiring backends.
 *
 * The Redis connection settings (`redis.Config`) and the circuit-breaker settings live with the
 * server backend in `:cache-redis` (`RedisCacheConfig`), so that this API module stays pure-JVM and
 * free of any backend dependency — the `provideCache` factory that unites them also lives there,
 * matching how Go's `cache/config` package sits above both `cache/memory` and `cache/redis`.
 *
 * @param expiry the TTL an expiring backend applies to writes; defaults to one hour, matching Go's
 *   `envDefault:"1h"` and its `if expiry <= 0 { expiry = time.Hour }` guard.
 */
public data class CacheConfig(
    val provider: CacheProvider,
    val expiry: Duration = 1.hours,
)
