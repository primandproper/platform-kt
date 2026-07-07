package com.primandproper.platform.circuitbreaking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerConfigTest {
    @Test
    fun validatesAValidConfig() {
        CircuitBreakerConfig().apply {
            name = "svc"
            failureThreshold = 5
            resetTimeout = 10.seconds
            halfOpenMaxProbes = 2
        }.validate() // does not throw
    }

    @Test
    fun rejectsMissingName() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig().apply { name = "" }.validate()
        }
    }

    @Test
    fun rejectsNonPositiveThreshold() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig().apply {
                name = "svc"
                failureThreshold = 0
            }.validate()
        }
    }

    @Test
    fun rejectsZeroResetTimeout() {
        assertFailsWith<IllegalArgumentException> {
            CircuitBreakerConfig().apply {
                name = "svc"
                resetTimeout = Duration.ZERO
            }.validate()
        }
    }

    @Test
    fun ensureDefaultsFillsUnsetFields() {
        val cfg =
            CircuitBreakerConfig().apply {
                name = ""
                failureThreshold = 0
                resetTimeout = Duration.ZERO
                halfOpenMaxProbes = 0
            }
        cfg.ensureDefaults()

        assertEquals(DEFAULT_NAME, cfg.name)
        assertEquals(DEFAULT_FAILURE_THRESHOLD, cfg.failureThreshold)
        assertEquals(DEFAULT_RESET_TIMEOUT, cfg.resetTimeout)
        assertEquals(DEFAULT_HALF_OPEN_MAX_PROBES, cfg.halfOpenMaxProbes)
    }

    @Test
    fun ensureDefaultsDoesNotOverrideSetValues() {
        val cfg =
            CircuitBreakerConfig().apply {
                name = "svc"
                failureThreshold = 7
                resetTimeout = 5.seconds
                halfOpenMaxProbes = 3
            }
        cfg.ensureDefaults()

        assertEquals("svc", cfg.name)
        assertEquals(7, cfg.failureThreshold)
        assertEquals(5.seconds, cfg.resetTimeout)
        assertEquals(3, cfg.halfOpenMaxProbes)
    }
}
