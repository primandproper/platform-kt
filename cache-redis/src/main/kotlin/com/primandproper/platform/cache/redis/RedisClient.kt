package com.primandproper.platform.cache.redis

/**
 * The minimal Redis command surface [RedisCache] needs. Port of platform-go's `redisClient`
 * interface — it exists for the same reason: so the cache can be unit-tested against a fake without a
 * live server (`redisclient_mock_test.go` in Go). [LettuceRedisClient] is the production adapter over
 * a real connection.
 *
 * All methods suspend: the Lettuce adapter bridges each `RedisFuture` (a `CompletionStage`) to a
 * coroutine via `kotlinx.coroutines.future.await()`.
 */
public interface RedisClient {
    /** Returns the raw stored string at [key], or `null` on a miss (Go's `redis.Nil`). */
    public suspend fun get(key: String): String?

    /** Stores [value] at [key] with a millisecond [ttlMillis]; a non-positive TTL stores without expiry. */
    public suspend fun set(
        key: String,
        value: String,
        ttlMillis: Long,
    )

    /** Returns the stored strings for [keys], in order; a `null` element is a missing key. */
    public suspend fun mget(keys: List<String>): List<String?>

    /** Deletes [key]. */
    public suspend fun del(key: String)

    /**
     * Stores every `keys[i]` with `values[i]`, applying the single [ttlMillis] TTL to all of them in
     * one round trip — the analog of Go's `batchSetScript` EVAL. A non-positive TTL stores without
     * expiry.
     */
    public suspend fun setBatch(
        keys: List<String>,
        values: List<String>,
        ttlMillis: Long,
    )

    /** Verifies the server is reachable. */
    public suspend fun ping()
}
