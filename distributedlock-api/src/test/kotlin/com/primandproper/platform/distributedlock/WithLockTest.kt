package com.primandproper.platform.distributedlock

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

/** Covers the [Locker.withLock] bracket helper. */
class WithLockTest {
    private fun locker(timeSource: TestTimeSource = TestTimeSource()): MemoryLocker =
        MemoryLocker(
            com.primandproper.platform.observability.noopObserver("test"),
            timeSource,
        )

    @Test
    fun `happy path returns the block result and releases the lock`() =
        runTest {
            val l = locker()

            val result = l.withLock("k", 1.minutes) { 42 }
            assertEquals(42, result)

            // If withLock released, the key is free to re-acquire.
            assertTrue(!l.held.containsKey("k"))
            l.acquire("k", 1.minutes)
        }

    @Test
    fun `block exception propagates and the lock is still released`() =
        runTest {
            val l = locker()

            val boom = IllegalStateException("boom")
            val thrown =
                assertFailsWith<IllegalStateException> {
                    l.withLock<Unit>("k", 1.minutes) { throw boom }
                }
            assertEquals(boom, thrown)

            // The lock was released despite the failure, so the key is acquirable again.
            assertTrue(!l.held.containsKey("k"))
            l.acquire("k", 1.minutes)
        }

    @Test
    fun `release throwing LockNotHeld does not mask the block result`() =
        runTest {
            val clock = TestTimeSource()
            val l = locker(clock)

            // The lock expires while the block runs, so the finally's release() throws
            // LockNotHeldException. That must not replace the block's return value.
            val result =
                l.withLock("k", 50.milliseconds) {
                    clock += 80.milliseconds
                    "value"
                }
            assertEquals("value", result)
        }

    @Test
    fun `release throwing LockNotHeld does not mask a block exception`() =
        runTest {
            val clock = TestTimeSource()
            val l = locker(clock)

            val boom = IllegalStateException("boom")
            val thrown =
                assertFailsWith<IllegalStateException> {
                    l.withLock<Unit>("k", 50.milliseconds) {
                        clock += 80.milliseconds
                        throw boom
                    }
                }
            assertEquals(boom, thrown)
        }

    @Test
    fun `swallowed release failure is logged when a logger is supplied`() =
        runTest {
            val clock = TestTimeSource()
            val l = locker(clock)
            val logger = CapturingLogger()

            val result =
                l.withLock("k", 50.milliseconds, logger) {
                    clock += 80.milliseconds
                    "value"
                }
            assertEquals("value", result)

            val (_, err) = logger.errors.single()
            assertNotNull(err)
            assertTrue(isError<LockNotHeldException>(err as PlatformException))
        }

    @Test
    fun `acquisition failure propagates and the block never runs`() =
        runTest {
            val l = locker()
            l.acquire("shared", 1.minutes)

            var ran = false
            val e =
                assertFailsWith<PlatformException> {
                    l.withLock("shared", 1.minutes) { ran = true }
                }
            assertTrue(isError<LockNotAcquiredException>(e))
            assertTrue(!ran)
        }

    // A minimal Logger that records error(...) calls; every other method is a noop.
    private class CapturingLogger : Logger {
        val errors: MutableList<Pair<String, Throwable?>> = mutableListOf()

        override fun debug(msg: String) {}

        override fun info(msg: String) {}

        override fun warn(msg: String) {}

        override fun error(
            whatWasHappening: String,
            err: Throwable?,
        ) {
            errors.add(whatWasHappening to err)
        }

        override fun withName(name: String): Logger = this

        override fun withValue(
            key: String,
            value: Any?,
        ): Logger = this

        override fun withValues(values: Map<String, Any?>): Logger = this

        override fun withError(err: Throwable): Logger = this

        override fun withSpan(span: Span): Logger = this

        override fun clone(): Logger = this
    }
}
