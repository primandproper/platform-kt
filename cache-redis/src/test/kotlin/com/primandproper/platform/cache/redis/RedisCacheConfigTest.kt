package com.primandproper.platform.cache.redis

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `cache/redis/config_test.go`. */
class RedisCacheConfigTest {
    @Test
    fun `single address is single-node`() {
        assertFalse(RedisCacheConfig(listOf("localhost:6379")).clusterMode())
    }

    @Test
    fun `multiple addresses imply cluster mode`() {
        assertTrue(RedisCacheConfig(listOf("localhost:6379", "localhost:6380")).clusterMode())
    }

    @Test
    fun `explicit cluster flag forces cluster mode for a single seed`() {
        assertTrue(RedisCacheConfig(listOf("localhost:6379"), cluster = true).clusterMode())
    }

    @Test
    fun `validate accepts a configured address`() {
        RedisCacheConfig(listOf("localhost:6379")).validate()
    }

    @Test
    fun `validate rejects an empty address list`() {
        assertFailsWith<IllegalArgumentException> { RedisCacheConfig(emptyList()).validate() }
    }
}
