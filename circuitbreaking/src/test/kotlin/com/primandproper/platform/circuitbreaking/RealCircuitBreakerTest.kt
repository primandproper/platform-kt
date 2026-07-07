package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.noopObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
            CircuitBreakerConfig().apply {
                name = "test"
                failureThreshold = threshold
                resetTimeout = 1.seconds
                this.halfOpenMaxProbes = halfOpenMaxProbes
            }.also { it.ensureDefaults() }
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
            assertTrue(isError(thrown, ErrCircuitBroken))
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
    fun degradesToNoopOnInvalidConfig() {
        val cb = CircuitBreaker(CircuitBreakerConfig().apply { failureThreshold = -1 })
        assertSame(NoopCircuitBreaker, cb)
    }

    @Test
    fun buildsRealBreakerForValidConfig() {
        val cb = CircuitBreaker(CircuitBreakerConfig().apply { name = "svc" })
        assertTrue(cb is RealCircuitBreaker)
    }

    @Test
    fun unsetNameStillBuildsRealBreaker() {
        // ensureDefaults fills the blank name with UNKNOWN before validation, mirroring the Go provider.
        val cb = CircuitBreaker(CircuitBreakerConfig())
        assertTrue(cb is RealCircuitBreaker)
    }
}
