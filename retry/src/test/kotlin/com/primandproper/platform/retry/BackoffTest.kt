package com.primandproper.platform.retry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Direct tests of the internal backoff/jitter math, independent of coroutine virtual time. */
class BackoffTest {
    @Test
    fun `grows by the multiplier and caps at maxDelay`() {
        val backoff =
            Backoff(
                initialDelay = 100.milliseconds,
                maxDelay = 1.seconds,
                multiplier = 2.0,
                useJitter = false,
            )

        assertEquals(100.milliseconds, backoff.next())
        assertEquals(200.milliseconds, backoff.next())
        assertEquals(400.milliseconds, backoff.next())
        assertEquals(800.milliseconds, backoff.next())
        // 1600ms would exceed maxDelay, so it's capped at 1s.
        assertEquals(1.seconds, backoff.next())
        assertEquals(1.seconds, backoff.next())
    }

    @Test
    fun `jitter stays within the equal-jitter bounds across many draws`() {
        repeat(1_000) { seed ->
            val backoff =
                Backoff(
                    initialDelay = 100.milliseconds,
                    maxDelay = 100.milliseconds,
                    multiplier = 1.0,
                    useJitter = true,
                    random = Random(seed),
                )

            val sleep = backoff.next()
            assertTrue(sleep >= 50.milliseconds, "expected >= 50ms, was $sleep")
            assertTrue(sleep < 100.milliseconds, "expected < 100ms, was $sleep")
        }
    }

    @Test
    fun `jitter is skipped when the delay is too small to halve`() {
        val backoff =
            Backoff(
                initialDelay = 1.nanoseconds,
                maxDelay = 10.milliseconds,
                multiplier = 2.0,
                useJitter = true,
            )

        // Must not throw (Random.nextLong requires a positive bound) and must return the
        // unmodified delay when it's too small to halve.
        assertEquals(1.nanoseconds, backoff.next())
    }
}
