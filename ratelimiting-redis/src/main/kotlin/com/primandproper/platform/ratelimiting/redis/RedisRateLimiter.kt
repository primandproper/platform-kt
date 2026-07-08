package com.primandproper.platform.ratelimiting.redis

import com.primandproper.platform.identifiers.newUuid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.ratelimiting.RateLimiter
import kotlin.math.ceil

/**
 * `slidingWindowScript` atomically checks and increments a per-key sliding-window counter, returning
 * `1` when the request is admitted and `0` when it is throttled. Copied verbatim from platform-go's
 * `ratelimiting/redis.slidingWindowScript` so behavior — and the `EVAL` cache key on the server — is
 * identical across the two ports. `internal` so same-module tests can assert the exact script sent.
 */
internal const val SLIDING_WINDOW_SCRIPT: String = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window_ms)
local count = redis.call('ZCARD', key)
if count < limit then
    redis.call('ZADD', key, now, member)
    redis.call('PEXPIRE', key, window_ms * 2)
    return 1
end
return 0
"""

/**
 * A Redis-backed, distributed [RateLimiter] using a sliding-window counter. Port of platform-go's
 * `ratelimiting/redis.rateLimiter`.
 *
 * The token-bucket-style `(requestsPerSec, burstSize)` config is mapped onto a sliding window exactly
 * as Go does: allow up to `limit` requests per window, where `limit == max(burstSize, 1)` and the
 * window is the time to accrue a full burst at the steady rate (`ceil(limit / rate * 1000)` ms). This
 * honors the burst and avoids the old `int64(requestsPerSec)` truncation that floored any sub-1 rate
 * to a limit of `0` (rejecting everything). The ZADD member is `"<now>-<uuid>"` — unique per request,
 * because ZADD on a duplicate member only updates its score, which would collapse same-millisecond
 * requests into one entry and silently bypass the limit under load.
 *
 * Each `allow` opens an [Observer] span recording the key and the allow/deny outcome — the platform-kt
 * analog of Go's allowed/rejected counters; a thrown backend error is recorded on the span and
 * rethrown (Go returns it after bumping `errorCounter`).
 *
 * TODO(metrics): Go records allowed/rejected/error counters through a metrics provider; there is no
 * metrics pillar in platform-kt's observability-api yet, so those are a documented seam.
 *
 * @param requestsPerSec the steady rate the window is sized against.
 * @param burstSize the per-window request cap.
 */
public class RedisRateLimiter internal constructor(
    private val o11y: Observer,
    private val client: RedisClient,
    private val requestsPerSec: Double,
    private val burstSize: Int,
    private val clock: () -> Long,
    private val newMember: () -> String,
) : RateLimiter {
    /**
     * @param client the command surface; [LettuceRedisClient] in production, a fake in tests.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        client: RedisClient,
        requestsPerSec: Double,
        burstSize: Int,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(Observer(NAME, logger, tracerProvider), client, requestsPerSec, burstSize, System::currentTimeMillis, ::newUuid)

    override suspend fun allow(key: String): Boolean =
        o11y.span("Allow") {
            set(Keys.NAME, key)

            val now = clock() // epoch millis, matching Go's time.Now().UnixMilli()
            val limit = maxOf(burstSize.toLong(), 1L)
            val rate = if (requestsPerSec <= 0.0) 1.0 else requestsPerSec
            val windowMs = maxOf(ceil(limit.toDouble() / rate * 1000.0).toLong(), 1L)
            val member = "$now-${newMember()}"

            val result =
                client.evalInt(
                    SLIDING_WINDOW_SCRIPT,
                    listOf("ratelimit:$key"),
                    // args order matches the script's ARGV: now, windowMs, limit, member.
                    listOf(now, windowMs, limit, member),
                )

            val allowed = result == 1L
            // TODO(metrics): Go adds 1 to allowedCounter/rejectedCounter here.
            set(ALLOWED, allowed)
            allowed
        }

    override fun close() {
        client.close()
    }

    private companion object {
        const val NAME = "redis_rate_limiter"
        const val ALLOWED = "allowed"
    }
}
