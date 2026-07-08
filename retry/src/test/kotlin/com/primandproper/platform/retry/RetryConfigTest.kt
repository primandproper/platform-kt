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
    fun `ensureDefaults sets defaults for zero values`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 0
                initialDelay = Duration.ZERO
                maxDelay = Duration.ZERO
                multiplier = 0.0
            }

        cfg.ensureDefaults()

        assertEquals(DEFAULT_MAX_ATTEMPTS, cfg.maxAttempts)
        assertEquals(100.milliseconds, cfg.initialDelay)
        assertEquals(5.seconds, cfg.maxDelay)
        assertEquals(2.0, cfg.multiplier)
    }

    @Test
    fun `ensureDefaults preserves non-zero values`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 7
                initialDelay = 1.seconds
                maxDelay = 10.seconds
                multiplier = 3.0
            }

        cfg.ensureDefaults()

        assertEquals(7, cfg.maxAttempts)
        assertEquals(1.seconds, cfg.initialDelay)
        assertEquals(10.seconds, cfg.maxDelay)
        assertEquals(3.0, cfg.multiplier)
    }

    @Test
    fun `ensureDefaults clamps invalid values`() {
        // A multiplier below 1 shrinks the backoff and negative delays are nonsensical;
        // ensureDefaults must replace them since policy construction can't reject them.
        val cfg =
            RetryConfig().apply {
                maxAttempts = 2
                initialDelay = (-1).seconds
                maxDelay = (-1).seconds
                multiplier = 0.5
            }

        cfg.ensureDefaults()

        assertEquals(2, cfg.maxAttempts)
        assertEquals(100.milliseconds, cfg.initialDelay)
        assertEquals(5.seconds, cfg.maxDelay)
        assertEquals(2.0, cfg.multiplier)
    }

    @Test
    fun `validate accepts a valid config`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 1
                initialDelay = 1.milliseconds
                maxDelay = 1.seconds
                multiplier = 2.0
            }

        cfg.validate()
    }

    @Test
    fun `validate rejects a non-positive maxAttempts`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 0
                initialDelay = 1.milliseconds
                maxDelay = 1.seconds
                multiplier = 2.0
            }

        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }

    @Test
    fun `validate rejects a non-positive initialDelay`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 1
                initialDelay = Duration.ZERO
                maxDelay = 1.seconds
                multiplier = 2.0
            }

        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }

    @Test
    fun `validate rejects a non-positive maxDelay`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 1
                initialDelay = 1.milliseconds
                maxDelay = Duration.ZERO
                multiplier = 2.0
            }

        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }

    @Test
    fun `validate rejects a sub-1 multiplier`() {
        val cfg =
            RetryConfig().apply {
                maxAttempts = 1
                initialDelay = 1.milliseconds
                maxDelay = 1.seconds
                multiplier = 0.5
            }

        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }
}
