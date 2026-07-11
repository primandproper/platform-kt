package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.messagequeue.ByteArrayMessageEncoder
import com.primandproper.platform.messagequeue.MessageQueueProvider
import com.primandproper.platform.messagequeue.noop.NoopConsumerProvider
import com.primandproper.platform.messagequeue.noop.NoopPublisherProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Port of platform-go's `messagequeue/config` provider-selection tests. */
class ProvideMessageQueueTest {
    private fun config() = RedisMessageQueueConfig(queueAddresses = listOf("fake:6379"))

    @Test
    fun `redis provider builds a redis-backed publisher provider`() =
        runTest {
            val provider =
                publisherProvider(
                    MessageQueueProvider.REDIS,
                    ByteArrayMessageEncoder,
                    redisConfig = config(),
                    redisClient = FakeRedisPubSubClient(),
                )
            assertTrue(provider is RedisPublisherProvider<*>)
            assertNotNull(provider.publisher("t"))
        }

    @Test
    fun `redis provider builds a redis-backed consumer provider`() {
        val provider =
            consumerProvider(
                MessageQueueProvider.REDIS,
                redisConfig = config(),
                redisClient = FakeRedisPubSubClient(),
            )
        assertTrue(provider is RedisConsumerProvider)
    }

    @Test
    fun `redis provider without a config fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            publisherProvider(MessageQueueProvider.REDIS, ByteArrayMessageEncoder)
        }
        assertFailsWith<IllegalArgumentException> { consumerProvider(MessageQueueProvider.REDIS) }
    }

    @Test
    fun `unported providers fall back to noop`() {
        for (p in listOf(MessageQueueProvider.SQS, MessageQueueProvider.PUBSUB, MessageQueueProvider.KAFKA, null)) {
            assertTrue(publisherProvider(p, ByteArrayMessageEncoder) is NoopPublisherProvider<*>, "publisher for $p")
            assertTrue(consumerProvider(p) is NoopConsumerProvider, "consumer for $p")
        }
    }
}
