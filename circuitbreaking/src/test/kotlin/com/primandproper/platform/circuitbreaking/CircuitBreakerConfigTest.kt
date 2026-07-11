package com.primandproper.platform.circuitbreaking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerConfigTest {
    @Test
    fun buildsAValidConfig() {
        CircuitBreakerConfig(
            name = "svc",
            failureThreshold = 5,
            resetTimeout = 10.seconds,
            halfOpenMaxProbes = 2,
        ) // does not throw
    }

    @Test
    fun rejectsMissingName() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig(name = "")
        }
    }

    @Test
    fun rejectsNonPositiveThreshold() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig(name = "svc", failureThreshold = 0)
        }
    }

    @Test
    fun rejectsZeroResetTimeout() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig(name = "svc", resetTimeout = Duration.ZERO)
        }
    }

    @Test
    fun omittedNumericFieldsResolveToTheirDefaults() {
        val cfg = CircuitBreakerConfig(name = "svc")

        assertEquals(DEFAULT_FAILURE_THRESHOLD, cfg.failureThreshold)
        assertEquals(DEFAULT_RESET_TIMEOUT, cfg.resetTimeout)
        assertEquals(DEFAULT_HALF_OPEN_MAX_PROBES, cfg.halfOpenMaxProbes)
    }

    @Test
    fun explicitFieldsArePreserved() {
        val cfg =
            CircuitBreakerConfig(
                name = "svc",
                failureThreshold = 7,
                resetTimeout = 5.seconds,
                halfOpenMaxProbes = 3,
            )

        assertEquals("svc", cfg.name)
        assertEquals(7, cfg.failureThreshold)
        assertEquals(5.seconds, cfg.resetTimeout)
        assertEquals(3, cfg.halfOpenMaxProbes)
    }
}
