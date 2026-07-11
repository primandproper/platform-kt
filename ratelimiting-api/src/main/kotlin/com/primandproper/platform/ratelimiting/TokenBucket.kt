package com.primandproper.platform.ratelimiting

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A single-key token bucket — the refill math of Go's `golang.org/x/time/rate.Limiter`, ported
 * bit-for-bit so allow/deny sequences match the original. platform-go's in-memory limiter holds one
 * `rate.Limiter` per key; [InMemoryRateLimiter] holds one [TokenBucket] per key for the same effect.
 *
 * The bucket refills continuously: [limit] tokens accrue per second, capped at [burst]. `x/time/rate`
 * starts a fresh limiter with `tokens = 0` and `last` at the zero time, so the very first [allow]
 * `advance` sees an effectively-infinite elapsed span and fills the bucket to `burst` (unless
 * `limit <= 0`, in which case no tokens ever accrue and the bucket stays empty — the `rate.Limit(0)`
 * edge, faithfully reproduced by the null [lastMark] sentinel below).
 *
 * Elapsed time is measured off an injected [kotlin.time.TimeSource] ([TimeSource.Monotonic] in
 * production, a `TestTimeSource` in tests) so refill can be exercised deterministically without real
 * sleeps — the same move `:circuitbreaking` makes for its reset deadline. Only *elapsed* time matters
 * here, so a monotonic source is the right base (immune to wall-clock jumps). Every mutation is guarded
 * by `@Synchronized`, mirroring the `sync.Mutex` inside `rate.Limiter`.
 */
internal class TokenBucket(
    private val limit: Double,
    private val burst: Int,
    private val timeSource: TimeSource,
) {
    // Tokens currently in the bucket; fractional because refill is continuous.
    private var tokens: Double = 0.0

    // Mark of the last accepted request. `null` stands in for x/time/rate's zero-time `last`: the
    // first advance() treats it as "infinitely long ago" and fills to burst.
    private var lastMark: TimeMark? = null

    /** Equivalent of `rate.Limiter.Allow()` / `AllowN(now, 1)`: consume one token if available. */
    @Synchronized
    fun allow(): Boolean = allowN(1)

    /** Equivalent of `rate.Limiter.reserveN(now, n, 0)` restricted to its `ok` result. */
    private fun allowN(n: Int): Boolean {
        // advance() yields the token count after accrual; subtract the request as x/time/rate does.
        val remaining = advance() - n
        // reserveN's `ok` with maxFutureReserve == 0: the request must fit the burst and leave the
        // bucket non-negative (waitDuration <= 0 iff remaining >= 0).
        val ok = n <= burst && remaining >= 0.0
        if (ok) {
            // Commit the new state only on success — reserveN leaves `last`/`tokens` untouched otherwise.
            lastMark = timeSource.markNow()
            tokens = remaining
        }
        return ok
    }

    /**
     * Port of `rate.Limiter.advance`: tokens accrued since [lastMark], capped at [burst]. A monotonic
     * source never runs backwards, so no `t.Before(last)` clamp is needed; the first call (null
     * [lastMark]) sees an infinite span and fills to burst.
     */
    private fun advance(): Double {
        val elapsed = lastMark?.elapsedNow() ?: Duration.INFINITE
        val accrued = tokens + tokensFromDuration(elapsed)
        val burstF = burst.toDouble()
        return if (accrued > burstF) burstF else accrued
    }

    /** Port of `rate.Limit.tokensFromDuration`: `seconds * limit`, or `0` when the limit is non-positive. */
    private fun tokensFromDuration(elapsed: Duration): Double {
        if (limit <= 0.0) return 0.0
        return elapsed.toDouble(DurationUnit.SECONDS) * limit
    }
}
