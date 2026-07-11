package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.EmptyTopicNameException
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of platform-go's `messagequeue/redis` consumer tests, exercised against an in-memory fake. */
@OptIn(ExperimentalCoroutinesApi::class)
class RedisConsumerTest {
    @Test
    fun `Consume drives the handler and records the operation`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val obs = RecordingObserver()
            val received = mutableListOf<ByteArray>()
            val consumer = RedisConsumer(obs, client, "topic", ConsumerHandler { received += it }, NoopCircuitBreaker)

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { consumer.consume() }
            val sub = client.subscriptions.single()
            sub.emit("hello".encodeToByteArray())
            advanceUntilIdle()

            assertEquals("hello", received.single().decodeToString())
            val op = obs.operations.last { it.name == "consume_message" }
            assertTrue(op.ended)
            assertEquals("topic", op.values["topic"])
            assertEquals(5, op.values["length"])
            assertTrue(op.errors.isEmpty())

            job.cancelAndJoin()
            assertTrue(sub.closed)
        }

    @Test
    fun `a handler failure is recorded on the span and the loop keeps consuming`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val obs = RecordingObserver()
            val seen = mutableListOf<String>()
            val handler =
                ConsumerHandler { payload ->
                    val s = payload.decodeToString()
                    seen += s
                    if (s == "bad") throw RuntimeException("handler failed")
                }
            val consumer = RedisConsumer(obs, client, "topic", handler, NoopCircuitBreaker)

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { consumer.consume() }
            val sub = client.subscriptions.single()
            sub.emit("bad".encodeToByteArray())
            sub.emit("good".encodeToByteArray())
            advanceUntilIdle()

            // The failing message was recorded as an error, and the next message was still delivered.
            assertEquals(listOf("bad", "good"), seen)
            val failedOp = obs.operations.first { it.name == "consume_message" }
            assertTrue(failedOp.errors.isNotEmpty())
            assertTrue(failedOp.ended)

            job.cancelAndJoin()
        }

    @Test
    fun `each delivered message runs under the circuit breaker`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val breaker = RecordingCircuitBreaker()
            val consumer = RedisConsumer(RecordingObserver(), client, "topic", ConsumerHandler {}, breaker)

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { consumer.consume() }
            client.subscriptions.single().emit("x".encodeToByteArray())
            advanceUntilIdle()

            assertEquals(1, breaker.executeCount)
            assertEquals(1, breaker.successCount)
            job.cancelAndJoin()
        }

    @Test
    fun `messages emits each payload and cancelling collection closes the subscription`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val consumer = RedisConsumer(RecordingObserver(), client, "topic", ConsumerHandler {}, NoopCircuitBreaker)
            val received = mutableListOf<String>()

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    consumer.messages().collect { received += it.decodeToString() }
                }
            val sub = client.subscriptions.single()
            sub.emit("hello".encodeToByteArray())
            sub.emit("world".encodeToByteArray())
            advanceUntilIdle()

            assertEquals(listOf("hello", "world"), received)

            job.cancelAndJoin()
            assertTrue(sub.closed)
        }

    @Test
    fun `messages closes the subscription when the stream ends`() =
        runTest {
            val client = FakeRedisPubSubClient()
            val consumer = RedisConsumer(RecordingObserver(), client, "topic", ConsumerHandler {}, NoopCircuitBreaker)

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    consumer.messages().collect {}
                }
            val sub = client.subscriptions.single()
            sub.close() // ends the underlying message flow
            advanceUntilIdle()
            job.cancelAndJoin()

            assertTrue(sub.closed)
        }

    @Test
    fun `the provider rejects an empty topic`() =
        runTest {
            val provider = redisConsumerProvider(config(), client = FakeRedisPubSubClient())
            assertFailsWith<EmptyTopicNameException> { provider.consumer("") {} }
        }

    @Test
    fun `the provider caches one consumer per topic and handler`() =
        runTest {
            val provider = redisConsumerProvider(config(), client = FakeRedisPubSubClient())
            val handler = ConsumerHandler {}
            val first = provider.consumer("t", handler)
            val second = provider.consumer("t", handler)
            assertSame(first, second)
        }

    @Test
    fun `a different handler on the same topic gets its own consumer`() =
        runTest {
            // Regression for P2-5: a second, distinct handler must not silently receive the consumer
            // bound to the first handler.
            val provider = redisConsumerProvider(config(), client = FakeRedisPubSubClient())
            val first = provider.consumer("t", ConsumerHandler {})
            val second = provider.consumer("t", ConsumerHandler {})
            assertNotSame(first, second)
        }

    @Test
    fun `the provider closes the shared client`() =
        runTest {
            val client = FakeRedisPubSubClient()
            redisConsumerProvider(config(), client = client).close()
            assertTrue(client.closed)
        }

    private fun config() = RedisMessageQueueConfig(queueAddresses = listOf("fake:6379"))
}
