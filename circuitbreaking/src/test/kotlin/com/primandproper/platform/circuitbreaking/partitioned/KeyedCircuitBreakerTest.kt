package com.primandproper.platform.circuitbreaking.partitioned

import com.primandproper.platform.circuitbreaking.CircuitBreakerConfig
import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeyedCircuitBreakerTest {
    @Test
    fun returnsDedicatedBreakerForRegisteredKey() {
        val dedicated = RecordingCircuitBreaker()
        val global = RecordingCircuitBreaker()
        val keyed = KeyedCircuitBreaker(global, mapOf("123" to dedicated))

        assertSame(dedicated, keyed.forKey("123"))
        assertSame(dedicated, keyed["123"]) // operator sugar
    }

    @Test
    fun fallsBackToGlobalForUnregisteredKey() {
        val dedicated = RecordingCircuitBreaker()
        val global = RecordingCircuitBreaker()
        val keyed = KeyedCircuitBreaker(global, mapOf("123" to dedicated))

        assertSame(global, keyed.forKey("456"))
    }

    @Test
    fun nilBreakersFallBackToGlobalForEveryKey() {
        val global = RecordingCircuitBreaker()
        val keyed = KeyedCircuitBreaker(global)

        assertSame(global, keyed.forKey("anything"))
    }

    @Test
    fun configFactoryFailsLoudlyOnInvalidBaseConfig() {
        // README contract: misconfiguration fails loudly at startup rather than degrading to a noop.
        assertFailsWith<IllegalArgumentException> {
            KeyedCircuitBreaker(KeyedCircuitBreakerConfig(base = CircuitBreakerConfig(name = "svc", failureThreshold = -1)))
        }
    }

    @Test
    fun configFactoryFailsLoudlyOnBlankPartitionKey() {
        assertFailsWith<IllegalArgumentException> {
            KeyedCircuitBreaker(
                KeyedCircuitBreakerConfig(base = CircuitBreakerConfig(name = "svc"), keys = listOf("")),
            )
        }
    }

    @Test
    fun configFactoryBuildsForValidConfig() {
        val keyed =
            KeyedCircuitBreaker(
                KeyedCircuitBreakerConfig(base = CircuitBreakerConfig(name = "svc"), keys = listOf("tenant-1")),
            )
        assertTrue(keyed is DefaultKeyedCircuitBreaker)
    }

    @Test
    fun breaksOneKeyInIsolation() =
        runTest {
            // The broken tenant is pinned open; the healthy global breaker keeps other tenants flowing.
            val broken = RecordingCircuitBreaker(reject = true)
            val global = RecordingCircuitBreaker()
            val keyed = KeyedCircuitBreaker(global, mapOf("123" to broken))

            val thrown = assertFailsWith<PlatformException> { keyed.forKey("123").execute { "unreachable" } }
            assertTrue(isError<CircuitBrokenException>(thrown))

            assertEquals("ok", keyed.forKey("456").execute { "ok" })
            assertEquals(1, global.successCount)
        }
}
