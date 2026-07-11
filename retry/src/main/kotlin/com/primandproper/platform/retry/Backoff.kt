package com.primandproper.platform.retry

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Computes the exponential-backoff-with-jitter delay sequence, shared by [ExponentialBackoffPolicy]
 * and [retryWithPolicy]. Stateful: each [next] call returns the delay to sleep for the current
 * attempt and advances to the next one, mirroring the `delay` variable threaded through
 * platform-go's `Execute` loop.
 *
 * Because that state escalates and never resets, one instance corresponds to exactly one retry
 * sequence: callers construct a fresh [Backoff] at the start of each `execute()` invocation / Flow
 * collection rather than sharing it on a long-lived object, so the mutation here is never contended.
 */
internal class Backoff(
    initialDelay: Duration,
    private val maxDelay: Duration,
    private val multiplier: Double,
    private val useJitter: Boolean,
    private val random: Random = Random.Default,
) {
    private var current: Duration = initialDelay

    fun next(): Duration {
        val sleep = if (useJitter) jittered(current) else current
        current = minOf(current * multiplier, maxDelay)
        return sleep
    }

    /**
     * "Equal jitter": sleeps somewhere in `[delay/2, delay)`, matching platform-go's
     * `delay - half + rand.Int64N(half)` (`half = delay/2`).
     *
     * `half > 0` guards [Random.nextLong], which requires a positive bound — the same guard as Go's
     * `half > 0` check, needed because a sub-2ns delay truncates `half` to zero.
     */
    private fun jittered(delay: Duration): Duration {
        val nanos = delay.inWholeNanoseconds
        val half = nanos / 2
        if (half <= 0) return delay
        val jitter = random.nextLong(half)
        return (nanos - half + jitter).nanoseconds
    }
}
