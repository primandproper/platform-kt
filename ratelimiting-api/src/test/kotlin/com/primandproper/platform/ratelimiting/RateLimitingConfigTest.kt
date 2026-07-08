package com.primandproper.platform.ratelimiting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Port of the provider-name and defaults/validation cases from platform-go's `config/config_test.go`. */
class RateLimitingConfigTest {
    @Test
    fun `providers resolve case-insensitively and trimmed`() {
        assertEquals(RateLimitingProvider.MEMORY, RateLimitingProvider.fromValue("  Memory "))
        assertEquals(RateLimitingProvider.REDIS, RateLimitingProvider.fromValue("redis"))
        assertEquals(RateLimitingProvider.NOOP, RateLimitingProvider.fromValue("noop"))
    }

    @Test
    fun `blank provider resolves to noop`() {
        assertEquals(RateLimitingProvider.NOOP, RateLimitingProvider.fromValue(""))
        assertEquals(RateLimitingProvider.NOOP, RateLimitingProvider.fromValue("   "))
    }

    @Test
    fun `unknown provider resolves to null`() {
        assertNull(RateLimitingProvider.fromValue("token-bucket"))
    }

    @Test
    fun `ensureDefaults fills zero fields`() {
        val cfg = RateLimitingConfig(provider = RateLimitingProvider.MEMORY, requestsPerSec = 0.0, burstSize = 0).ensureDefaults()
        assertEquals(10.0, cfg.requestsPerSec)
        assertEquals(20, cfg.burstSize)
    }

    @Test
    fun `ensureDefaults preserves non-zero fields`() {
        val cfg = RateLimitingConfig(requestsPerSec = 5.0, burstSize = 10).ensureDefaults()
        assertEquals(5.0, cfg.requestsPerSec)
        assertEquals(10, cfg.burstSize)
    }

    @Test
    fun `validate accepts a non-negative budget`() {
        RateLimitingConfig(requestsPerSec = 1.0, burstSize = 1).validate()
    }

    @Test
    fun `validate rejects a negative rate`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitingConfig(requestsPerSec = -1.0, burstSize = 1).validate()
        }
    }

    @Test
    fun `validate rejects a negative burst`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitingConfig(requestsPerSec = 1.0, burstSize = -1).validate()
        }
    }
}
