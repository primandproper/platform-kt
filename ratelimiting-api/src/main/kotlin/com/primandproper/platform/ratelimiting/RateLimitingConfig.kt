package com.primandproper.platform.ratelimiting

/**
 * The supported rate-limiter providers. Port of platform-go's `ProviderNoop` / `ProviderMemory` /
 * `ProviderRedis` constants. [value] is the wire/string form validated against configuration.
 */
public enum class RateLimitingProvider(
    public val value: String,
) {
    NOOP("noop"),
    MEMORY("memory"),
    REDIS("redis"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive). An empty value maps
         * to [NOOP] — Go's `case "", ProviderNoop:` treats a blank provider as the no-op default —
         * while an unrecognized name returns `null` so callers can reject it (Go's `default:` error).
         */
        public fun fromValue(value: String): RateLimitingProvider? {
            val normalized = value.trim().lowercase()
            if (normalized.isEmpty()) return NOOP
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider-agnostic rate-limiter configuration. Port of the portable part of platform-go's
 * `ratelimitingcfg.Config`: the chosen [provider] and the token-bucket budget ([requestsPerSec],
 * [burstSize]).
 *
 * The Redis connection settings (`redis.Config`) and the `provideRateLimiter` factory that unites all
 * three backends live with the server backend in `:ratelimiting-redis`, so this API module stays
 * pure-JVM and free of any backend dependency — matching how Go's `ratelimiting/config` package sits
 * above `ratelimiting`, `ratelimiting/noop`, and `ratelimiting/redis`.
 *
 * @param provider which backend to build; defaults to [RateLimitingProvider.NOOP].
 * @param requestsPerSec the steady refill rate applied by the memory/redis backends.
 * @param burstSize the bucket capacity applied by the memory/redis backends.
 */
public data class RateLimitingConfig(
    val provider: RateLimitingProvider = RateLimitingProvider.NOOP,
    val requestsPerSec: Double = DEFAULT_REQUESTS_PER_SEC,
    val burstSize: Int = DEFAULT_BURST_SIZE,
) {
    /**
     * Returns a copy with zero fields replaced by their defaults — the analog of Go's
     * `EnsureDefaults`, which only fills a field left at its zero value (`10.0` rps, a burst of `20`).
     */
    public fun ensureDefaults(): RateLimitingConfig =
        copy(
            requestsPerSec = if (requestsPerSec == 0.0) DEFAULT_REQUESTS_PER_SEC else requestsPerSec,
            burstSize = if (burstSize == 0) DEFAULT_BURST_SIZE else burstSize,
        )

    /**
     * Validates the budget, throwing [IllegalArgumentException] for a negative rate or burst — the
     * analog of Go's `validation.Min(0.0)` / `validation.Min(0)`.
     */
    public fun validate() {
        require(requestsPerSec >= 0.0) { "requestsPerSec must be >= 0, got $requestsPerSec" }
        require(burstSize >= 0) { "burstSize must be >= 0, got $burstSize" }
    }

    public companion object {
        public const val DEFAULT_REQUESTS_PER_SEC: Double = 10.0
        public const val DEFAULT_BURST_SIZE: Int = 20
    }
}
