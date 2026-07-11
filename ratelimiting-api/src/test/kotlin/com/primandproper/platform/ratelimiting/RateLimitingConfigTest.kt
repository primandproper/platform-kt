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
    fun `omitted fields default to the standard budget`() {
        val cfg = RateLimitingConfig(provider = RateLimitingProvider.MEMORY)
        assertEquals(10.0, cfg.requestsPerSec)
        assertEquals(20, cfg.burstSize)
    }

    @Test
    fun `explicit fields are preserved`() {
        val cfg = RateLimitingConfig(requestsPerSec = 5.0, burstSize = 10)
        assertEquals(5.0, cfg.requestsPerSec)
        assertEquals(10, cfg.burstSize)
    }

    @Test
    fun `construction accepts a non-negative budget`() {
        RateLimitingConfig(requestsPerSec = 1.0, burstSize = 1)
    }

    @Test
    fun `construction rejects a negative rate`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitingConfig(requestsPerSec = -1.0, burstSize = 1)
        }
    }

    @Test
    fun `construction rejects a negative burst`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitingConfig(requestsPerSec = 1.0, burstSize = -1)
        }
    }
}
