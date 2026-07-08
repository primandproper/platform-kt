package com.primandproper.platform.distributedlock.redis

/**
 * Connection settings for a Redis-backed distributed locker. Port of platform-go's
 * `distributedlock/redis.Config`.
 *
 * @param addresses one or more `host:port` addresses; at least one is required. A single address
 *   builds a standalone client; more than one selects cluster mode (see the TODO(cluster) seam on
 *   [LettuceRedisLockClient]).
 * @param username optional ACL username.
 * @param password optional password.
 * @param keyPrefix prepended to every lock key before it hits Redis, so lock keys are namespaced away
 *   from other data sharing the instance. Defaults to `lock:`, matching Go's `envDefault:"lock:"`.
 */
public data class RedisLockConfig(
    val addresses: List<String>,
    val username: String? = null,
    val password: String? = null,
    val keyPrefix: String = "lock:",
) {
    /**
     * Validates the config, throwing [IllegalArgumentException] when no address is configured — the
     * analog of Go's `validation.Field(&cfg.Addresses, validation.Required, validation.Length(1, 0))`.
     */
    public fun validate() {
        require(addresses.isNotEmpty()) { "at least one redis address is required" }
    }
}
