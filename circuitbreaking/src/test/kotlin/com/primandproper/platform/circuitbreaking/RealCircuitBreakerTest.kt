package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class RealCircuitBreakerTest {
    private class Boom : RuntimeException("boom")

    private fun breaker(
        timeSource: TestTimeSource,
        threshold: Int = 3,
        halfOpenMaxProbes: Int = 1,
    ): RealCircuitBreaker {
        val config =
            CircuitBreakerConfig(
                name = "test",
                failureThreshold = threshold,
                resetTimeout = 1.seconds,
                halfOpenMaxProbes = halfOpenMaxProbes,
            )
        return RealCircuitBreaker(config, noopObserver("test"), partition = null, timeSource = timeSource)
    }

    private suspend fun RealCircuitBreaker.fail() {
        assertFailsWith<Boom> { execute { throw Boom() } }
    }

    @Test
    fun startsClosedAndPassesThrough() =
        runTest {
            val cb = breaker(TestTimeSource())
            assertEquals(CircuitState.CLOSED, cb.state.value)
            assertEquals("ok", cb.execute { "ok" })
            assertEquals(CircuitState.CLOSED, cb.state.value)
        }

    @Test
    fun tripsAfterThresholdFailures() =
        runTest {
            val cb = breaker(TestTimeSource(), threshold = 3)
            cb.fail()
            cb.fail()
            assertEquals(CircuitState.CLOSED, cb.state.value) // 2 < 3
            cb.fail()
            assertEquals(CircuitState.OPEN, cb.state.value) // 3rd failure trips
        }

    @Test
    fun rejectsWhileOpenWithoutRunningBlock() =
        runTest {
            val cb = breaker(TestTimeSource(), threshold = 1)
            cb.fail()
            assertEquals(CircuitState.OPEN, cb.state.value)

            var ran = false
            val thrown =
                assertFailsWith<PlatformException> {
                    cb.execute {
                        ran = true
                        "unreachable"
                    }
                }
            assertFalse(ran)
            assertTrue(isError<CircuitBrokenException>(thrown))
        }

    @Test
    fun successResetsConsecutiveFailures() =
        runTest {
            val cb = breaker(TestTimeSource(), threshold = 3)
            cb.fail()
            cb.fail()
            cb.execute { "recovered" } // resets the streak
            cb.fail()
            cb.fail()
            assertEquals(CircuitState.CLOSED, cb.state.value) // streak never reached 3
        }

    @Test
    fun transitionsToHalfOpenThenClosesOnProbeSuccess() =
        runTest {
            val ts = TestTimeSource()
            val cb = breaker(ts, threshold = 1)
            cb.fail()
            assertEquals(CircuitState.OPEN, cb.state.value)

            ts += 1.seconds // reset timeout elapses

            var probed = false
            val result =
                cb.execute {
                    probed = true
                    "probe-ok"
                }
            assertTrue(probed)
            assertEquals("probe-ok", result)
            assertEquals(CircuitState.CLOSED, cb.state.value)
        }

    @Test
    fun reopensOnFailedProbe() =
        runTest {
            val ts = TestTimeSource()
            val cb = breaker(ts, threshold = 1)
            cb.fail()
            ts += 1.seconds

            cb.fail() // the admitted probe fails
            assertEquals(CircuitState.OPEN, cb.state.value)

            // Still rejecting immediately after re-opening (no time elapsed).
            assertFailsWith<PlatformException> { cb.execute { "unreachable" } }
        }

    @Test
    fun failsLoudlyOnInvalidConfig() {
        // README contract: misconfiguration fails loudly at startup, not silently at first use.
        assertFailsWith<IllegalArgumentException> {
            CircuitBreaker(CircuitBreakerConfig(name = "svc", failureThreshold = -1))
        }
    }

    @Test
    fun logsRejectionsRateLimitedWithBreakerContext() =
        runTest {
            val ts = TestTimeSource()
            val logger = RecordingLogger()
            val config =
                CircuitBreakerConfig(
                    name = "svc",
                    failureThreshold = 1,
                    // Long enough that the breaker stays OPEN across the rate-limit interval below.
                    resetTimeout = 10.minutes,
                )
            val cb =
                RealCircuitBreaker(
                    config,
                    Observer("svc", logger),
                    partition = "tenant-1",
                    timeSource = ts,
                )

            cb.fail() // trips the breaker OPEN
            // A storm of rejections in the same open window (no time elapsed between them).
            repeat(1_000) { assertFailsWith<PlatformException> { cb.execute { "unreachable" } } }

            val rejectionLines = logger.lines.filter { it.message == "circuit breaker rejecting calls" }
            // Rate-limited: only the first rejection in the window logs, not all 1,000.
            assertEquals(1, rejectionLines.size)
            val line = rejectionLines.single()
            assertEquals("svc", line.values["circuit_breaker"])
            assertEquals("tenant-1", line.values["partition"])
            assertEquals("OPEN", line.values["state"])

            // Once the rate-limit interval elapses, the next rejection logs again.
            ts += 6.seconds
            assertFailsWith<PlatformException> { cb.execute { "unreachable" } }
            assertEquals(2, logger.lines.count { it.message == "circuit breaker rejecting calls" })
        }

    @Test
    fun buildsRealBreakerForValidConfig() {
        val cb = CircuitBreaker(CircuitBreakerConfig(name = "svc"))
        assertTrue(cb is RealCircuitBreaker)
    }
}
