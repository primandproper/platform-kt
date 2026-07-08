package com.primandproper.platform.ratelimiting.redis

/**
 * The minimal Redis command surface [RedisRateLimiter] needs. Port of platform-go's `redisClient`
 * interface — it exists for the same reason: so the limiter can be unit-tested against a fake without
 * a live server (`redis_test.go`'s `mockRedisClient` in Go). [LettuceRedisClient] is the production
 * adapter over a real connection.
 *
 * [evalInt] suspends: the Lettuce adapter bridges each `RedisFuture` (a `CompletionStage`) to a
 * coroutine via `kotlinx.coroutines.future.await()`.
 */
public interface RedisClient {
    /**
     * Runs [script] with [keys] and [args] and returns its integer reply, the analog of Go's
     * `client.Eval(...).Int64()`. [args] carry the mixed `Long`/`String` ARGV the sliding-window
     * script expects (`now`, `windowMs`, `limit`, `member`); the adapter renders each to its Redis
     * string form.
     */
    public suspend fun evalInt(
        script: String,
        keys: List<String>,
        args: List<Any>,
    ): Long

    /** Closes the underlying connection. Analog of Go's `Close() error`. */
    public fun close()
}
