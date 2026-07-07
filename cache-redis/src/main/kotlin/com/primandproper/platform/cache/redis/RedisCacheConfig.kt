package com.primandproper.platform.cache.redis

/**
 * Connection settings for a Redis-backed cache. Port of platform-go's `cache/redis.Config`.
 *
 * @param queueAddresses one or more `host:port` addresses; at least one is required.
 * @param username optional ACL username.
 * @param password optional password.
 * @param cluster forces Redis Cluster mode even for a single seed address (see [clusterMode]).
 */
public data class RedisCacheConfig(
    val queueAddresses: List<String>,
    val username: String? = null,
    val password: String? = null,
    val cluster: Boolean = false,
) {
    /**
     * Reports whether the client should run in Redis Cluster mode. A cluster can be reached through a
     * single seed address, so the explicit [cluster] flag is honored in addition to the multi-address
     * heuristic — otherwise a single-seed cluster is misclassified as single-node and multi-slot
     * `getMany`/`setMany` fail with `CROSSSLOT`. Mirrors Go's `Config.clusterMode`.
     */
    public fun clusterMode(): Boolean = cluster || queueAddresses.size > 1

    /**
     * Validates the config, throwing [IllegalArgumentException] when no address is configured — the
     * analog of Go's `validation.Field(&cfg.QueueAddresses, validation.Required, validation.Length(1, 0))`.
     */
    public fun validate() {
        require(queueAddresses.isNotEmpty()) { "at least one redis address is required" }
    }
}
