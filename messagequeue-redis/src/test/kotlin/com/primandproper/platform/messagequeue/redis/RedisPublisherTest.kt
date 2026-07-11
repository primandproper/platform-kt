package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.messagequeue.EmptyTopicNameException
import com.primandproper.platform.messagequeue.MessageEncoder
import com.primandproper.platform.messagequeue.StringMessageEncoder
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of platform-go's `messagequeue/redis` publisher tests, exercised against an in-memory fake. */
class RedisPublisherTest {
    private fun publisher(
        client: RedisPubSubClient = FakeRedisPubSubClient(),
        observer: RecordingObserver = RecordingObserver(),
        encoder: MessageEncoder<String> = StringMessageEncoder,
        breaker: com.primandproper.platform.circuitbreaking.CircuitBreaker = NoopCircuitBreaker,
        topic: String = "topic",
    ) = RedisPublisher(observer, client, encoder, topic, breaker)

    @Test
    fun `Publish encodes and sends under an observed operation`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val obs = RecordingObserver()
            publisher(client = client, observer = obs).publish("hello")

            assertEquals(1, client.published.size)
            assertEquals("topic", client.published[0].first)
            assertEquals("hello", client.published[0].second.decodeToString())

            val op = obs.operations.last { it.name == "Publish" }
            assertTrue(op.ended)
            assertTrue(op.errors.isEmpty())
            assertEquals("topic", op.values["topic"])
            assertEquals(5, op.values["length"])
        }

    @Test
    fun `Publish records the encode error on the span and rethrows`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val obs = RecordingObserver()
            val failing = MessageEncoder<String> { throw RuntimeException("boom") }

            assertFailsWith<RuntimeException> { publisher(client = client, observer = obs, encoder = failing).publish("x") }

            val op = obs.operations.last { it.name == "Publish" }
            assertTrue(op.errors.isNotEmpty())
            assertTrue(op.ended)
            assertTrue(client.published.isEmpty())
        }

    @Test
    fun `PublishAsync swallows the error instead of throwing`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val failing = MessageEncoder<String> { throw RuntimeException("boom") }
            publisher(client = client, encoder = failing).publishAsync("x")
            assertTrue(client.published.isEmpty())
        }

    @Test
    fun `an open circuit breaker rejects the publish before the send`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val breaker = RecordingCircuitBreaker(reject = true)

            val error = assertFailsWith<Throwable> { publisher(client = client, breaker = breaker).publish("x") }

            assertTrue(error is CircuitBrokenException)
            assertTrue(client.published.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }

    @Test
    fun `close does not close the shared client used by other topics`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val provider = redisPublisherProvider(config(), StringMessageEncoder, client = client)

            val pub1 = provider.publisher("topic-1")
            val pub2 = provider.publisher("topic-2")
            pub1.close()

            pub2.publish("still works")
            assertEquals(1, client.published.size)
            assertTrue(!client.closed)
        }

    @Test
    fun `the provider rejects an empty topic`() =
        runTest {
            val provider = redisPublisherProvider(config(), StringMessageEncoder, client = FakeRedisPubSubClient())
            assertFailsWith<EmptyTopicNameException> { provider.publisher("") }
        }

    @Test
    fun `the provider caches one publisher per topic`() =
        runTest {
            val provider = redisPublisherProvider(config(), StringMessageEncoder, client = FakeRedisPubSubClient())
            val first = provider.publisher("t")
            val second = provider.publisher("t")
            assertSame(first, second)
        }

    @Test
    fun `the provider pings and closes the shared client`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val provider = redisPublisherProvider(config(), StringMessageEncoder, client = client)
            provider.ping()
            provider.close()
            assertEquals(1, client.pingCount)
            assertTrue(client.closed)
        }

    private fun config() = RedisMessageQueueConfig(queueAddresses = listOf("fake:6379"))
}
