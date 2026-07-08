package com.primandproper.platform.ratelimiting.redis

/**
 * Connection settings for a Redis-backed rate limiter. Port of platform-go's `ratelimiting/redis.Config`.
 *
 * @param addresses one or more `host:port` addresses; at least one is required. Go builds a single
 *   `redis.NewClient` for one address and a `redis.NewClusterClient` for several (see [clusterMode]).
 * @param username optional ACL username.
 * @param password optional password.
 */
public data class RedisRateLimitingConfig(
    val addresses: List<String>,
    val username: String? = null,
    val password: String? = null,
) {
    /**
     * Reports whether the client should run in Redis Cluster mode. Mirrors Go's
     * `len(cfg.Addresses) > 1` branch in `NewRedisRateLimiter`.
     */
    public fun clusterMode(): Boolean = addresses.size > 1

    /**
     * Validates the config, throwing [IllegalArgumentException] when no address is configured — the
     * analog of Go's `validation.Field(&cfg.Addresses, validation.Required, validation.Length(1, 0))`.
     */
    public fun validate() {
        require(addresses.isNotEmpty()) { "at least one redis address is required" }
    }
}
