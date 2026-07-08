package com.primandproper.platform.messagequeue.redis

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `messagequeue/redis` config test. */
class RedisMessageQueueConfigTest {
    @Test
    fun `a config with an address validates`() {
        RedisMessageQueueConfig(queueAddresses = listOf("localhost:6379"), username = "u", password = "p").validate()
    }

    @Test
    fun `a config with no addresses fails validation`() {
        assertFailsWith<IllegalArgumentException> { RedisMessageQueueConfig(queueAddresses = emptyList()).validate() }
    }

    @Test
    fun `clusterMode reflects the address count and the explicit flag`() {
        assertFalse(RedisMessageQueueConfig(listOf("a:1")).clusterMode())
        assertTrue(RedisMessageQueueConfig(listOf("a:1", "b:2")).clusterMode())
        assertTrue(RedisMessageQueueConfig(listOf("a:1"), cluster = true).clusterMode())
    }
}
