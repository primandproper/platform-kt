package com.primandproper.platform.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

/**
 * Covers the provider-name validation from platform-go's `config/config_test.go`
 * (`TestConfig_ValidateWithContext`); the Redis-specific and `ProvideCache` cases live in
 * `:cache-redis`, where the Redis config and factory reside.
 */
class CacheConfigTest {
    @Test
    fun `memory provider resolves`() {
        assertEquals(CacheProvider.MEMORY, CacheProvider.fromValue("memory"))
    }

    @Test
    fun `redis provider resolves case-insensitively and trimmed`() {
        assertEquals(CacheProvider.REDIS, CacheProvider.fromValue("  Redis "))
    }

    @Test
    fun `invalid provider name resolves to null`() {
        assertNull(CacheProvider.fromValue("vault"))
    }

    @Test
    fun `expiry defaults to one hour`() {
        assertEquals(1.hours, CacheConfig(CacheProvider.MEMORY).expiry)
    }
}
