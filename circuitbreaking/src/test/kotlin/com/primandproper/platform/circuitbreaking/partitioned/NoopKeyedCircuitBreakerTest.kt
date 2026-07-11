package com.primandproper.platform.circuitbreaking.partitioned

import com.primandproper.platform.circuitbreaking.CircuitState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NoopKeyedCircuitBreakerTest {
    @Test
    fun alwaysProceedsForAnyKey() =
        runTest {
            val cb = NoopKeyedCircuitBreaker.forKey("123")
            assertEquals("ok", cb.execute { "ok" })
            assertEquals(CircuitState.CLOSED, cb.state.value)

            // Failures never trip it.
            repeat(50) { runCatching { cb.execute { throw RuntimeException("boom") } } }
            assertEquals(CircuitState.CLOSED, cb.state.value)
        }
}
