package com.primandproper.platform.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Mirrors platform-go's `config_test.go`. */
class RetryConfigTest {
    @Test
    fun `omitted fields default to the standard policy`() {
        val cfg = RetryConfig()

        assertEquals(DEFAULT_MAX_ATTEMPTS, cfg.maxAttempts)
        assertEquals(100.milliseconds, cfg.initialDelay)
        assertEquals(5.seconds, cfg.maxDelay)
        assertEquals(2.0, cfg.multiplier)
    }

    @Test
    fun `explicit fields are preserved`() {
        val cfg =
            RetryConfig(
                maxAttempts = 7,
                initialDelay = 1.seconds,
                maxDelay = 10.seconds,
                multiplier = 3.0,
            )

        assertEquals(7, cfg.maxAttempts)
        assertEquals(1.seconds, cfg.initialDelay)
        assertEquals(10.seconds, cfg.maxDelay)
        assertEquals(3.0, cfg.multiplier)
    }

    @Test
    fun `construction rejects a non-positive maxAttempts`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(maxAttempts = 0) }
    }

    @Test
    fun `construction rejects a non-positive initialDelay`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(initialDelay = Duration.ZERO) }
    }

    @Test
    fun `construction rejects a non-positive maxDelay`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(maxDelay = Duration.ZERO) }
    }

    @Test
    fun `construction rejects a sub-1 multiplier`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(multiplier = 0.5) }
    }
}
