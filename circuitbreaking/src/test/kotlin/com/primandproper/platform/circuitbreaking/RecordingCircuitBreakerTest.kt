package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.errors.PlatformException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordingCircuitBreakerTest {
    @Test
    fun recordsSuccessesAndFailuresWhenPassingThrough() =
        runTest {
            val cb = RecordingCircuitBreaker()

            assertEquals("ok", cb.execute { "ok" })
            assertFailsWith<RuntimeException> { cb.execute { throw RuntimeException("boom") } }

            assertEquals(2, cb.executeCount)
            assertEquals(1, cb.successCount)
            assertEquals(1, cb.failureCount)
            assertEquals(0, cb.rejectionCount)
            assertEquals(CircuitState.CLOSED, cb.state.value)
        }

    @Test
    fun rejectsEveryCallWhenPinnedOpen() =
        runTest {
            val cb = RecordingCircuitBreaker(reject = true)

            var ran = false
            assertFailsWith<PlatformException> { cb.execute { ran = true } }

            assertEquals(false, ran)
            assertEquals(1, cb.executeCount)
            assertEquals(1, cb.rejectionCount)
            assertEquals(0, cb.successCount)
            assertEquals(CircuitState.OPEN, cb.state.value)
        }
}
