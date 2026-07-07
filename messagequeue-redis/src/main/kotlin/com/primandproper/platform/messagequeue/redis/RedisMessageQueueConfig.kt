package com.primandproper.platform.messagequeue.redis

/**
 * Connection settings for a Redis-backed message queue. Port of platform-go's
 * `messagequeue/redis.Config`.
 *
 * The Redis connection settings live with the backend here (not in `:messagequeue-api`), so the API
 * module stays pure-JVM and free of any backend dependency — mirroring how `:cache-redis` owns
 * `RedisCacheConfig`.
 *
 * @param queueAddresses one or more `host:port` addresses; at least one is required.
 * @param username optional ACL username.
 * @param password optional password.
 * @param cluster forces Redis Cluster mode even for a single seed address (see [clusterMode]).
 */
public data class RedisMessageQueueConfig(
    val queueAddresses: List<String>,
    val username: String? = null,
    val password: String? = null,
    val cluster: Boolean = false,
) {
    /**
     * Reports whether the client should run in Redis Cluster mode. platform-go picks a
     * `ClusterClient` when more than one address is configured; the explicit [cluster] flag is honored
     * in addition, so a single-seed cluster is not misclassified as single-node.
     */
    public fun clusterMode(): Boolean = cluster || queueAddresses.size > 1

    /**
     * Validates the config, throwing [IllegalArgumentException] when no address is configured — the
     * analog of Go's `validation.Field(&cfg.QueueAddresses, validation.Required)`.
     */
    public fun validate() {
        require(queueAddresses.isNotEmpty()) { "at least one redis address is required" }
    }
}
