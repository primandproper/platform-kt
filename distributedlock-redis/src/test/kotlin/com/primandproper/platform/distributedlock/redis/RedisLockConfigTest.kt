package com.primandproper.platform.distributedlock.redis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Port of platform-go's `distributedlock/redis/config_test.go`. */
class RedisLockConfigTest {
    @Test
    fun `missing addresses is rejected`() {
        assertFailsWith<IllegalArgumentException> { RedisLockConfig(addresses = emptyList()).validate() }
    }

    @Test
    fun `happy path validates`() {
        RedisLockConfig(addresses = listOf("localhost:6379")).validate()
    }

    @Test
    fun `keyPrefix defaults to lock`() {
        assertEquals("lock:", RedisLockConfig(addresses = listOf("localhost:6379")).keyPrefix)
    }
}
