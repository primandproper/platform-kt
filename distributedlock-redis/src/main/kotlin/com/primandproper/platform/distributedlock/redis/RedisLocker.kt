package com.primandproper.platform.distributedlock.redis

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.distributedlock.EmptyKeyException
import com.primandproper.platform.distributedlock.InvalidTtlException
import com.primandproper.platform.distributedlock.Lock
import com.primandproper.platform.distributedlock.LockNotAcquiredException
import com.primandproper.platform.distributedlock.LockNotHeldException
import com.primandproper.platform.distributedlock.Locker
import com.primandproper.platform.errors.isError
import com.primandproper.platform.identifiers.newUlid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A Redis-backed [Locker]. Port of platform-go's `distributedlock/redis.locker`.
 *
 * Acquire is a single `SET key value NX PX ttl`: the ownership token (a fresh [newUlid]) is written
 * only if the key is absent, and the native PX expiry gives the lock its TTL. Release and Refresh run
 * compare-and-act Lua scripts that first check the stored value equals the caller's token, so a lock
 * is only ever released or its TTL extended by the caller that still owns it — never by a caller whose
 * lease already expired and was re-acquired by someone else.
 *
 * Every method opens an [Observer] span; genuine backend failures are recorded on it via `op.error`,
 * while expected control-flow outcomes (contention, lost ownership) are returned as the
 * [LockNotAcquiredException] / [LockNotHeldException] sentinels without being recorded as errors — mirroring how
 * platform-go calls `op.Error` only on real failures.
 *
 * The [CircuitBreaker] wraps each backend round trip. platform-go drives the breaker with the
 * primitive `CannotProceed()`/`Succeeded()`/`Failed()` trio; this port folds that into
 * [CircuitBreaker.execute]: an open breaker short-circuits with [CircuitBrokenException] before the call
 * runs, a thrown backend error counts a failure, and a normal return (including the healthy
 * "contended"/"not held" replies) counts a success — so contention never trips the breaker, exactly
 * as in Go.
 *
 * TODO(metrics): Go records acquire/release/refresh/contend/error counters and a latency histogram
 * through a metrics provider; there is no metrics pillar in platform-kt's observability-api yet.
 */
public class RedisLocker internal constructor(
    private val o11y: Observer,
    private val client: RedisLockClient,
    private val circuitBreaker: CircuitBreaker,
    private val keyPrefix: String,
) : Locker {
    /**
     * @param config connection settings; validated (a non-empty address list is required) when the
     *   production client is built.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param circuitBreaker optional breaker; defaults to the always-closed [NoopCircuitBreaker],
     *   matching Go's `EnsureCircuitBreaker`.
     * @param client an override [RedisLockClient] (a fake in tests); when `null` a lazily-connecting
     *   [LettuceRedisLockClient] is built from [config].
     */
    public constructor(
        config: RedisLockConfig,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
        circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
        client: RedisLockClient? = null,
    ) : this(
        Observer(NAME, logger, tracerProvider),
        client ?: buildLettuceClient(config),
        circuitBreaker,
        config.keyPrefix,
    )

    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock {
        val op = o11y.begin("Acquire")
        try {
            op.set(Keys.NAME, key).set("lock.ttl", ttl)

            if (key.isEmpty()) throw EmptyKeyException()
            // Reject sub-millisecond TTLs (covers zero/negative too): a positive sub-ms TTL truncates to
            // 0 millis and would otherwise become a permanent, never-expiring lock. See the client guard.
            if (ttl < 1.milliseconds) throw InvalidTtlException()

            val token = newUlid()
            val fullKey = keyPrefix + key
            val acquired =
                try {
                    circuitBreaker.execute { client.setNx(fullKey, token, ttl.inWholeMilliseconds) }
                } catch (t: Throwable) {
                    if (isError<CircuitBrokenException>(t)) throw t
                    throw op.error(t, "acquiring lock \"$key\"")
                }

            op.set("lock.outcome", if (acquired) "acquired" else "contended")
            // Backend healthy, contention is the expected outcome — a sentinel, not a recorded error.
            if (!acquired) throw LockNotAcquiredException()

            return RedisLock(this, key, fullKey, token, ttl)
        } finally {
            op.end()
        }
    }

    override suspend fun ping() {
        o11y.span("Ping") { client.ping() }
    }

    override suspend fun close() {
        client.close()
    }

    // The compare-and-delete release path called by lock handles.
    private suspend fun release(
        fullKey: String,
        token: String,
    ) {
        val op = o11y.begin("Release")
        try {
            op.set("lock.full_key", fullKey)
            val res =
                try {
                    circuitBreaker.execute { client.eval(RELEASE_SCRIPT, listOf(fullKey), listOf(token)) }
                } catch (t: Throwable) {
                    if (isError<CircuitBrokenException>(t)) throw t
                    throw op.error(t, "releasing lock")
                }
            if (res == 0L) throw LockNotHeldException()
        } finally {
            op.end()
        }
    }

    // The compare-and-pexpire refresh path called by lock handles.
    private suspend fun refresh(
        fullKey: String,
        token: String,
        ttl: Duration,
    ) {
        val op = o11y.begin("Refresh")
        try {
            op.set("lock.full_key", fullKey).set("lock.ttl", ttl)
            // A sub-ms TTL truncates to 0, and `PEXPIRE key 0` deletes the lock while reporting success.
            if (ttl < 1.milliseconds) throw InvalidTtlException()
            val res =
                try {
                    circuitBreaker.execute {
                        client.eval(REFRESH_SCRIPT, listOf(fullKey), listOf(token, ttl.inWholeMilliseconds.toString()))
                    }
                } catch (t: Throwable) {
                    if (isError<CircuitBrokenException>(t)) throw t
                    throw op.error(t, "refreshing lock")
                }
            if (res == 0L) throw LockNotHeldException()
        } finally {
            op.end()
        }
    }

    // The redis-backed Lock handle. Nested so it can reach the locker's private release/refresh.
    private class RedisLock(
        private val locker: RedisLocker,
        override val key: String,
        private val fullKey: String,
        private val token: String,
        ttl: Duration,
    ) : Lock {
        private var currentTtl: Duration = ttl
        override val ttl: Duration get() = currentTtl

        override suspend fun release() {
            locker.release(fullKey, token)
        }

        override suspend fun refresh(ttl: Duration) {
            locker.refresh(fullKey, token, ttl)
            currentTtl = ttl
        }
    }

    private companion object {
        const val NAME = "redis_distributed_lock"

        // Atomically deletes the lock key only if its value matches the caller's token. Returns 1 on
        // a successful release, 0 when the caller no longer owns the lock (expired, stolen, released).
        const val RELEASE_SCRIPT = """
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
"""

        // Atomically extends the lock's TTL (ARGV[2] millis) only if its value matches the caller's
        // token. Returns 1 on success, 0 when the caller no longer owns the lock.
        const val REFRESH_SCRIPT = """
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("PEXPIRE", KEYS[1], ARGV[2])
else
    return 0
end
"""

        fun buildLettuceClient(config: RedisLockConfig): RedisLockClient {
            config.validate()
            return LettuceRedisLockClient(config)
        }
    }
}
