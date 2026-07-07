package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ensureCircuitBreaker
import com.primandproper.platform.messagequeue.Consumer
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.ConsumerProvider
import com.primandproper.platform.messagequeue.EmptyTopicNameException
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A Redis pub/sub-backed [Consumer]. Port of platform-go's `messagequeue/redis.redisConsumer`.
 *
 * [consume] subscribes to the topic, then collects the subscription's message flow, opening a
 * `consume_message` Observer span per message that records the topic and payload length and runs the
 * handler under the injected [CircuitBreaker]. A handler failure is recorded on the span (Go's
 * `op.Acknowledge`) and does not stop the loop; cancelling the collecting coroutine ends the loop and
 * closes the subscription (Go's `defer subscription.Close()` on `ctx.Done()`/`stopChan`). The
 * subscription close runs under [NonCancellable] so cancellation still releases the server-side
 * subscription rather than leaking it.
 *
 * TODO(metrics): Go records a `<topic>_consumed` counter through a metrics provider; there is no
 * metrics pillar in platform-kt's observability-api yet, so that is a documented seam.
 */
public class RedisConsumer internal constructor(
    private val o11y: Observer,
    private val client: RedisPubSubClient,
    private val topic: String,
    private val handler: ConsumerHandler,
    private val circuitBreaker: CircuitBreaker,
) : Consumer {
    override suspend fun consume() {
        val subscription = client.subscribe(topic)
        try {
            subscription.messages().collect { payload ->
                o11y.span("consume_message") {
                    set(TOPIC_KEY, topic)
                    set(Keys.LENGTH, payload.size)
                    try {
                        circuitBreaker.execute { handler.handle(payload) }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        // Record the handler failure on the op (Go's op.Acknowledge) and keep consuming.
                        acknowledge(error, "handling message")
                    }
                }
            }
        } finally {
            withContext(NonCancellable) { subscription.close() }
        }
    }
}

/**
 * A Redis-backed [ConsumerProvider]. Port of platform-go's `redis.consumerProvider`: it owns the
 * shared [RedisPubSubClient] and caches one [RedisConsumer] per topic under a coroutine [Mutex].
 */
public class RedisConsumerProvider internal constructor(
    private val o11y: Observer,
    private val client: RedisPubSubClient,
    private val circuitBreaker: CircuitBreaker,
    private val logger: Logger?,
    private val tracerProvider: TracerProvider?,
) : ConsumerProvider {
    private val cacheMutex = Mutex()
    private val consumerCache: MutableMap<String, Consumer> = mutableMapOf()

    override suspend fun provideConsumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer {
        if (topic.isEmpty()) throw EmptyTopicNameException()
        return cacheMutex.withLock {
            consumerCache.getOrPut(topic) {
                RedisConsumer(
                    o11y = Observer("${topic}_consumer", logger, tracerProvider),
                    client = client,
                    topic = topic,
                    handler = handler,
                    circuitBreaker = circuitBreaker,
                )
            }
        }
    }

    override fun close() {
        try {
            client.close()
        } catch (error: Throwable) {
            o11y.logger.error("closing redis consumer client", error)
        }
    }
}

/**
 * Builds a Redis-backed [ConsumerProvider]. Port of platform-go's `redis.ProvideRedisConsumerProvider`.
 *
 * @param config connection settings; [RedisMessageQueueConfig.validate] is applied up front.
 * @param circuitBreaker optional breaker wrapping each per-message handler run; defaults to noop.
 * @param client an override [RedisPubSubClient] (a fake in tests); when `null` a lazily-connecting
 *   [LettuceRedisPubSubClient] is built from [config].
 */
public fun provideRedisConsumerProvider(
    config: RedisMessageQueueConfig,
    circuitBreaker: CircuitBreaker? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    client: RedisPubSubClient? = null,
): ConsumerProvider {
    config.validate()
    return RedisConsumerProvider(
        o11y = Observer(CONSUMER_PROVIDER_NAME, logger, tracerProvider),
        client = client ?: LettuceRedisPubSubClient(config),
        circuitBreaker = ensureCircuitBreaker(circuitBreaker),
        logger = logger,
        tracerProvider = tracerProvider,
    )
}

private const val CONSUMER_PROVIDER_NAME = "redis_consumer_provider"
