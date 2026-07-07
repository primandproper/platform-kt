package com.primandproper.platform.ratelimiting

/**
 * A single-key token bucket — the refill math of Go's `golang.org/x/time/rate.Limiter`, ported
 * bit-for-bit so allow/deny sequences match the original. platform-go's in-memory limiter holds one
 * `rate.Limiter` per key; [InMemoryRateLimiter] holds one [TokenBucket] per key for the same effect.
 *
 * The bucket refills continuously: [limit] tokens accrue per second, capped at [burst]. `x/time/rate`
 * starts a fresh limiter with `tokens = 0` and `last` at the zero time, so the very first [allow]
 * `advance` sees an effectively-infinite elapsed span and fills the bucket to `burst` (unless
 * `limit <= 0`, in which case no tokens ever accrue and the bucket stays empty — the `rate.Limit(0)`
 * edge, faithfully reproduced by the [Long.MIN_VALUE] sentinel below).
 *
 * Time is passed in as a monotonic nanosecond reading (`System.nanoTime()` in production, a controlled
 * value in tests) so refill can be exercised deterministically without real sleeps — the same move
 * `:retry` makes for its jitter. Every mutation is guarded by `@Synchronized`, mirroring the
 * `sync.Mutex` inside `rate.Limiter`.
 */
internal class TokenBucket(
    private val limit: Double,
    private val burst: Int,
) {
    // Tokens currently in the bucket; fractional because refill is continuous.
    private var tokens: Double = 0.0

    // Nanosecond reading of the last accepted request. Long.MIN_VALUE stands in for x/time/rate's
    // zero-time `last`: the first advance() treats it as "infinitely long ago" and fills to burst.
    private var lastNanos: Long = Long.MIN_VALUE

    /** Equivalent of `rate.Limiter.Allow()` / `AllowN(now, 1)`: consume one token if available. */
    @Synchronized
    fun allow(nowNanos: Long): Boolean = allowN(nowNanos, 1)

    /** Equivalent of `rate.Limiter.reserveN(now, n, 0)` restricted to its `ok` result. */
    private fun allowN(
        nowNanos: Long,
        n: Int,
    ): Boolean {
        // advance() yields the token count after accrual; subtract the request as x/time/rate does.
        val remaining = advance(nowNanos) - n
        // reserveN's `ok` with maxFutureReserve == 0: the request must fit the burst and leave the
        // bucket non-negative (waitDuration <= 0 iff remaining >= 0).
        val ok = n <= burst && remaining >= 0.0
        if (ok) {
            // Commit the new state only on success — reserveN leaves `last`/`tokens` untouched otherwise.
            lastNanos = nowNanos
            tokens = remaining
        }
        return ok
    }

    /** Port of `rate.Limiter.advance`: tokens accrued since [lastNanos], capped at [burst]. */
    private fun advance(nowNanos: Long): Double {
        var last = lastNanos
        if (last != Long.MIN_VALUE && nowNanos < last) {
            // Clock moved backwards; clamp so elapsed never goes negative (x/time/rate's `t.Before(last)`).
            last = nowNanos
        }
        val elapsedNanos = if (last == Long.MIN_VALUE) Long.MAX_VALUE else nowNanos - last
        val accrued = tokens + tokensFromDuration(elapsedNanos)
        val burstF = burst.toDouble()
        return if (accrued > burstF) burstF else accrued
    }

    /** Port of `rate.Limit.tokensFromDuration`: `seconds * limit`, or `0` when the limit is non-positive. */
    private fun tokensFromDuration(nanos: Long): Double {
        if (limit <= 0.0) return 0.0
        return (nanos / NANOS_PER_SECOND) * limit
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
