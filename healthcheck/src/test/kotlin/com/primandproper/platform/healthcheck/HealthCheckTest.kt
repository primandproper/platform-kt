package com.primandproper.platform.healthcheck

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `healthcheck_test.go`. */
class HealthCheckTest {
    /** Mirrors the Go test's inline `mockChecker`: an optional check body, healthy by default. */
    private class MockChecker(
        override val name: String,
        private val checkFn: (suspend () -> Unit)? = null,
    ) : Checker {
        override suspend fun check() {
            checkFn?.invoke()
        }
    }

    @Test
    fun `empty registry returns up`() =
        runTest {
            val result = Registry().checkAll()

            assertEquals(Status.UP, result.status)
            assertTrue(result.components.isEmpty())
        }

    @Test
    fun `all checkers up`() =
        runTest {
            val reg = Registry()
            reg.register(MockChecker(name = "a"))
            reg.register(MockChecker(name = "b"))

            val result = reg.checkAll()

            assertEquals(Status.UP, result.status)
            assertEquals(2, result.components.size)
            assertEquals(ComponentResult(Status.UP), result.components["a"])
            assertEquals(ComponentResult(Status.UP), result.components["b"])
        }

    @Test
    fun `one checker down`() =
        runTest {
            val reg = Registry()
            reg.register(MockChecker(name = "up"))
            reg.register(MockChecker(name = "down", checkFn = { throw RuntimeException("connection refused") }))

            val result = reg.checkAll()

            assertEquals(Status.DOWN, result.status)
            assertEquals(2, result.components.size)
            assertEquals(ComponentResult(Status.UP), result.components["up"])
            assertEquals(ComponentResult(Status.DOWN, "connection refused"), result.components["down"])
        }

    @Test
    fun `ignores nil checker`() =
        runTest {
            val reg = Registry()
            reg.register(null)
            reg.register(MockChecker(name = "a"))

            val result = reg.checkAll()

            assertEquals(Status.UP, result.status)
            assertEquals(1, result.components.size)
        }

    @Test
    fun `each check runs under a bounded timeout`() =
        runTest {
            // Go asserts every check receives a context with a deadline. Here the deadline is
            // structural: a check that overruns [DEFAULT_CHECK_TIMEOUT] is cancelled and reported
            // down. runTest's virtual clock advances the delay past the timeout without real waiting.
            val reg = Registry()
            reg.register(MockChecker(name = "fast"))
            reg.register(MockChecker(name = "slow", checkFn = { delay(DEFAULT_CHECK_TIMEOUT + 1.seconds) }))

            val result = reg.checkAll()

            assertEquals(Status.DOWN, result.status)
            assertEquals(ComponentResult(Status.UP), result.components["fast"])
            assertEquals(Status.DOWN, result.components["slow"]?.status)
        }
}
