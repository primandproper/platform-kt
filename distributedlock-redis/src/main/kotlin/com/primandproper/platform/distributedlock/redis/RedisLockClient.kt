package com.primandproper.platform.distributedlock.redis

/**
 * The minimal Redis command surface [RedisLocker] needs. Port of platform-go's `redisClient`
 * interface in `distributedlock/redis` — it exists for the same reason: so the locker can be
 * unit-tested against a fake without a live server. [LettuceRedisLockClient] is the production
 * adapter over a real connection.
 *
 * All methods suspend: the Lettuce adapter bridges each `RedisFuture` (a `CompletionStage`) to a
 * coroutine via `kotlinx.coroutines.future.await()`.
 */
public interface RedisLockClient {
    /**
     * `SET key value NX PX ttlMillis`: stores [value] at [key] only if [key] is absent, with a
     * millisecond expiry. Returns `true` when the write happened (the lock was acquired), `false`
     * when the key already existed (contention). The analog of Go's `SetNX(...).Result()`.
     */
    public suspend fun setNx(
        key: String,
        value: String,
        ttlMillis: Long,
    ): Boolean

    /**
     * Evaluates a Lua [script] against [keys] and [args], returning its integer reply. The locker's
     * release and refresh scripts are compare-and-act: they return 1 on success and 0 when the caller
     * no longer owns the key. The analog of Go's `Eval(...).Int64()`.
     */
    public suspend fun eval(
        script: String,
        keys: List<String>,
        args: List<String>,
    ): Long

    /** Verifies the server is reachable. */
    public suspend fun ping()

    /** Releases the client's resources. */
    public suspend fun close()
}
