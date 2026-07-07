package com.primandproper.platform.testutils.containers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Port of platform-go's `testutils/containers/containers_test.go`. Exercises the pure retry wrapper
 * against a fake container — no Docker daemon involved.
 */
class ContainersTest {
    private data class FakeContainer(val id: Int)

    @Test
    fun defaultRetryConfigMatchesGoDefaults() {
        val cfg = defaultRetryConfig()
        assertEquals(5, cfg.maxAttempts)
        assertEquals(1.seconds, cfg.initialDelay)
        assertFalse(cfg.useJitter)
    }

    @Test
    fun parseRunningTestsIsTrimmedAndCaseInsensitive() {
        assertTrue(parseRunningTests("true"))
        assertTrue(parseRunningTests("  TRUE  "))
        assertTrue(parseRunningTests("True"))
        assertFalse(parseRunningTests("false"))
        assertFalse(parseRunningTests("1"))
        assertFalse(parseRunningTests(""))
        assertFalse(parseRunningTests(null))
    }

    @Test
    fun startWithRetrySucceedsOnFirstAttempt() =
        runTest {
            var calls = 0
            val got =
                startWithRetry {
                    calls++
                    FakeContainer(1)
                }
            assertEquals(FakeContainer(1), got)
            assertEquals(1, calls)
        }

    @Test
    fun startWithRetryRetriesTransientFailuresThenSucceeds() =
        runTest {
            var calls = 0
            val got =
                startWithRetry {
                    calls++
                    if (calls < 3) error("flaky docker")
                    FakeContainer(calls)
                }
            assertEquals(3, calls)
            assertEquals(FakeContainer(3), got)
        }

    @Test
    fun startWithRetryGivesUpAfterMaxAttemptsAndRethrowsLastError() =
        runTest {
            var calls = 0
            val boom = IllegalStateException("always broken")
            val thrown =
                assertFailsWith<IllegalStateException> {
                    startWithRetry<FakeContainer> {
                        calls++
                        throw boom
                    }
                }
            assertSame(boom, thrown)
            assertEquals(5, calls)
        }

    @Test
    fun startWithRetryAbortsWhenContextIsAlreadyCancelled() =
        runTest {
            var calls = 0
            val cancelled = Job().also { it.cancel() }
            val thrown =
                assertFailsWith<CancellationException> {
                    withContext(cancelled) {
                        startWithRetry {
                            calls++
                            FakeContainer(0)
                        }
                    }
                }
            assertNotNull(thrown)
            // The policy checks the coroutine's active state before the first attempt.
            assertEquals(0, calls)
        }
}
