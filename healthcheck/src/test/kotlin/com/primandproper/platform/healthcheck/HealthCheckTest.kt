package com.primandproper.platform.healthcheck

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `an ancestor timeout propagates instead of being reported down`() =
        runTest {
            // The outer deadline (1s) is shorter than the per-check deadline (5s), so it is the
            // *ancestor* that cancels the probe. That cancellation must surface out of checkAll, not be
            // swallowed and mislabeled as a component that is down.
            val reg = Registry()
            reg.register(MockChecker(name = "slow", checkFn = { delay(100.seconds) }))

            assertFailsWith<TimeoutCancellationException> {
                withTimeout(1.seconds) { reg.checkAll() }
            }
        }

    @Test
    fun `logs a failure and then a recovery as a component flaps`() =
        runTest {
            val logger = CapturingLogger()
            val reg = Registry(logger)
            var healthy = false
            reg.register(MockChecker(name = "db", checkFn = { if (!healthy) throw RuntimeException("connection refused") }))

            reg.checkAll() // first probe: down -> a failure is logged
            assertEquals(1, logger.warnings.size, "a newly-failing component must be logged")
            assertEquals("db", logger.values["component"])

            healthy = true
            reg.checkAll() // second probe: back up -> a recovery is logged
            assertTrue(logger.infos.any { it.contains("recovered") }, "a recovered component must be logged")
        }

    @Test
    fun `does not re-log a component that stays down`() =
        runTest {
            val logger = CapturingLogger()
            val reg = Registry(logger)
            reg.register(MockChecker(name = "db", checkFn = { throw RuntimeException("still down") }))

            reg.checkAll()
            reg.checkAll()

            assertEquals(1, logger.warnings.size, "only the transition to down is logged, not every probe")
        }

    /** A [Logger] that captures warn/info messages and the fluent values, for asserting flap logging. */
    private class CapturingLogger : Logger by NoopLogger {
        val warnings = mutableListOf<String>()
        val infos = mutableListOf<String>()
        val values = mutableMapOf<String, Any?>()

        override fun warn(msg: String) {
            warnings += msg
        }

        override fun info(msg: String) {
            infos += msg
        }

        override fun withValue(
            key: String,
            value: Any?,
        ): Logger =
            apply {
                values[key] = value
            }

        override fun withError(err: Throwable): Logger = this
    }
}
