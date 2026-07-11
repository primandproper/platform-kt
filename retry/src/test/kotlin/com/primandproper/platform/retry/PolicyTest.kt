package com.primandproper.platform.retry

import com.primandproper.platform.observability.Level
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Mirrors platform-go's `retry_test.go` (`TestExponentialBackoffPolicy_Execute`). */
class PolicyTest {
    @Test
    fun `success on first attempt`() =
        runTest {
            val policy = ExponentialBackoffPolicy(RetryConfig(maxAttempts = 3))
            var attempts = 0

            val result =
                policy.execute {
                    attempts++
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(1, attempts)
        }

    @Test
    fun `success after retries`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 5,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                        useJitter = false,
                    ),
                )
            var attempts = 0

            val result =
                policy.execute {
                    attempts++
                    if (attempts < 3) error("transient")
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(3, attempts)
        }

    @Test
    fun `throws last error after max attempts`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 3,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                        useJitter = false,
                    ),
                )
            var attempts = 0
            val finalFailure = IllegalStateException("final failure")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    policy.execute {
                        attempts++
                        if (attempts < 3) error("transient")
                        throw finalFailure
                    }
                }

            assertSame(finalFailure, thrown)
            assertEquals(3, attempts)
        }

    @Test
    fun `stops immediately on an UnretryableException`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 5,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                    ),
                )
            var attempts = 0
            val underlying = IllegalStateException("fatal")

            val thrown =
                assertFailsWith<UnretryableException> {
                    policy.execute {
                        attempts++
                        throw unretryable(underlying)
                    }
                }

            assertSame(underlying, thrown.cause)
            assertEquals(1, attempts)
        }

    @Test
    fun `stops immediately when retryIf rejects the error`() =
        runTest {
            class DoNotRetry : RuntimeException("nope")

            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 5,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                        retryIf = { it !is DoNotRetry },
                    ),
                )
            var attempts = 0

            assertFailsWith<DoNotRetry> {
                policy.execute {
                    attempts++
                    throw DoNotRetry()
                }
            }

            assertEquals(1, attempts)
        }

    // Regression: a sub-2ns delay makes half = nanos/2 truncate to 0, and Random.nextLong(0) throws
    // IllegalArgumentException. [Backoff] must skip jitter for a delay too small to halve rather than
    // crash. A [RetryConfig] can no longer carry a sub-millisecond delay, so this exercises the guard
    // on [Backoff] directly.
    @Test
    fun `backoff does not throw when the jitter delay is too small to halve`() {
        val backoff =
            Backoff(
                initialDelay = 1.nanoseconds,
                maxDelay = 10.nanoseconds,
                multiplier = 2.0,
                useJitter = true,
            )

        assertEquals(1.nanoseconds, backoff.next())
    }

    @Test
    fun `delays grow between attempts`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 4,
                        initialDelay = 100.milliseconds,
                        maxDelay = 10.seconds,
                        multiplier = 2.0,
                        useJitter = false,
                    ),
                )
            val timestamps = mutableListOf<Long>()
            val scheduler = testScheduler

            assertFailsWith<IllegalStateException> {
                policy.execute {
                    timestamps += scheduler.currentTime
                    error("transient")
                }
            }

            assertEquals(4, timestamps.size)
            val gaps = timestamps.zipWithNext { a, b -> b - a }
            // 100ms, 200ms, 400ms between the four attempts.
            assertEquals(listOf(100L, 200L, 400L), gaps)
        }

    @Test
    fun `jittered delay stays within the equal-jitter bounds`() =
        runTest {
            val initial = 100.milliseconds
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 2,
                        initialDelay = initial,
                        maxDelay = initial,
                        multiplier = 1.0,
                        useJitter = true,
                    ),
                )
            val timestamps = mutableListOf<Long>()
            val scheduler = testScheduler

            assertFailsWith<IllegalStateException> {
                policy.execute {
                    timestamps += scheduler.currentTime
                    error("transient")
                }
            }

            val gap = timestamps[1] - timestamps[0]
            // Equal jitter: sleeps in [delay/2, delay).
            assertTrue(gap in 50L until 100L, "expected gap in [50, 100), was $gap")
        }

    @Test
    fun `logs each retried attempt with its intermediate error when a logger is supplied`() =
        runTest {
            val logger = RecordingLogger()
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 3,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                        useJitter = false,
                    ),
                    logger,
                )
            var attempts = 0

            val result =
                policy.execute {
                    attempts++
                    if (attempts < 3) error("transient $attempts")
                    "ok"
                }

            assertEquals("ok", result)
            // Two intermediate failures were retried and logged; the successful third attempt is not.
            val warnings = logger.lines.filter { it.level == Level.WARN }
            assertEquals(2, warnings.size)
            assertEquals(1, warnings[0].values["attempt"])
            assertEquals(2, warnings[1].values["attempt"])
            assertEquals("transient 1", warnings[0].error?.message)
            assertEquals("transient 2", warnings[1].error?.message)
        }

    @Test
    fun `does not log when no logger is supplied`() =
        runTest {
            // Backward-compatible default: the logger param is absent, so nothing is logged and the
            // policy behaves exactly as before.
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 2,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                    ),
                )
            assertFailsWith<IllegalStateException> { policy.execute { error("boom") } }
        }

    @Test
    fun `does not swallow cancellation of the surrounding coroutine`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 10,
                        initialDelay = 1.seconds,
                        useJitter = false,
                    ),
                )
            var attempts = 0

            val job =
                async {
                    policy.execute {
                        attempts++
                        awaitCancellation()
                    }
                }
            advanceUntilIdle()
            job.cancel()

            assertFailsWith<CancellationException> { job.await() }
            assertEquals(1, attempts)
        }

    @Test
    fun `does not attempt the operation when already cancelled`() =
        runTest {
            val policy = ExponentialBackoffPolicy(RetryConfig(maxAttempts = 10))
            val childJob = Job(coroutineContext[Job])
            childJob.cancel()
            var attempted = false

            assertFailsWith<CancellationException> {
                withContext(childJob) {
                    policy.execute {
                        attempted = true
                        "should not run"
                    }
                }
            }

            assertFalse(attempted)
        }

    @Test
    fun `propagates a CancellationException thrown by the operation without retrying`() =
        runTest {
            val policy =
                ExponentialBackoffPolicy(
                    RetryConfig(
                        maxAttempts = 5,
                        initialDelay = 1.milliseconds,
                        maxDelay = 10.milliseconds,
                    ),
                )
            var attempts = 0

            assertFailsWith<CancellationException> {
                policy.execute {
                    attempts++
                    throw CancellationException("canceled upstream")
                }
            }

            assertEquals(1, attempts)
        }
}
