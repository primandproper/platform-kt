package com.primandproper.platform.ratelimiting.redis

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `ratelimiting/redis/redis_test.go` config cases. */
class RedisRateLimitingConfigTest {
    @Test
    fun `validate accepts a configured address`() {
        RedisRateLimitingConfig(listOf("localhost:6379")).validate()
    }

    @Test
    fun `validate rejects an empty address list`() {
        assertFailsWith<IllegalArgumentException> { RedisRateLimitingConfig(emptyList()).validate() }
    }

    @Test
    fun `single address is single-node`() {
        assertFalse(RedisRateLimitingConfig(listOf("localhost:6379")).clusterMode())
    }

    @Test
    fun `multiple addresses imply cluster mode`() {
        assertTrue(RedisRateLimitingConfig(listOf("localhost:6379", "localhost:6380")).clusterMode())
    }
}
