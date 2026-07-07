package com.primandproper.platform.circuitbreaking

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NoopCircuitBreakerTest {
    @Test
    fun alwaysPassesThroughAndStaysClosed() =
        runTest {
            assertEquals(CircuitState.CLOSED, NoopCircuitBreaker.state.value)
            assertEquals("ok", NoopCircuitBreaker.execute { "ok" })
            assertEquals(CircuitState.CLOSED, NoopCircuitBreaker.state.value)
        }

    @Test
    fun failuresNeverTripIt() =
        runTest {
            repeat(100) {
                runCatching { NoopCircuitBreaker.execute { throw RuntimeException("boom") } }
            }
            assertEquals(CircuitState.CLOSED, NoopCircuitBreaker.state.value)
        }

    @Test
    fun ensureCircuitBreakerReturnsNoopForNull() {
        assertEquals(NoopCircuitBreaker, ensureCircuitBreaker(null))
    }
}
