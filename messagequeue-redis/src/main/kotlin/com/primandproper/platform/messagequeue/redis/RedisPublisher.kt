package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.messagequeue.ByteArrayMessageEncoder
import com.primandproper.platform.messagequeue.EmptyTopicNameException
import com.primandproper.platform.messagequeue.MessageEncoder
import com.primandproper.platform.messagequeue.Publisher
import com.primandproper.platform.messagequeue.PublisherProvider
import com.primandproper.platform.messagequeue.StringMessageEncoder
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A Redis pub/sub-backed [Publisher]. Port of platform-go's `messagequeue/redis.redisPublisher`.
 *
 * [publish] opens an Observer span recording the topic and encoded length on both pillars (mirroring
 * `p.o11y.Begin(ctx)` / `op.Set(keys.TopicKey, ...)`), encodes [data] via the injected
 * [MessageEncoder], then runs the send under the injected [CircuitBreaker]. The breaker is the
 * resilience seam this port reattaches at the publish boundary (see the module doc): an open breaker
 * rejects with `ErrCircuitBroken` before the send, and a send failure counts as a breaker failure.
 *
 * TODO(metrics): Go records `<topic>_published` / `<topic>_publish_errors` counters and a
 * `<topic>_publish_latency_ms` histogram through a metrics provider; there is no metrics pillar in
 * platform-kt's observability-api yet, so those are a documented seam (the span already records the op).
 */
public class RedisPublisher<T : Any> internal constructor(
    private val o11y: Observer,
    private val client: RedisPubSubClient,
    private val encoder: MessageEncoder<T>,
    private val topic: String,
    private val circuitBreaker: CircuitBreaker,
) : Publisher<T> {
    // Close is a no-op: the underlying client is shared across every topic publisher, so closing one
    // topic must not close it; the provider closes the client once via close(). Mirrors Go's
    // redisPublisher.Stop (renamed to close under P3-12's single close convention).
    override suspend fun close() {}

    override suspend fun publish(data: T) {
        o11y.span("Publish") {
            set(TOPIC_KEY, topic)
            val bytes = encoder.encode(data)
            set(Keys.LENGTH, bytes.size)
            // The PUBLISH reply (subscriber count) is intentionally discarded; the span records the op.
            circuitBreaker.execute { client.publish(topic, bytes) }
        }
    }

    override suspend fun publishAsync(data: T) {
        try {
            publish(data)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            o11y.logger.error("publishing message", error)
        }
    }
}

/**
 * A Redis-backed [PublisherProvider]. Port of platform-go's `redis.publisherProvider`: it owns the
 * shared [RedisPubSubClient] and caches one [RedisPublisher] per topic under a coroutine [Mutex].
 */
public class RedisPublisherProvider<T : Any> internal constructor(
    private val o11y: Observer,
    private val client: RedisPubSubClient,
    private val encoder: MessageEncoder<T>,
    private val circuitBreaker: CircuitBreaker,
    private val logger: Logger,
    private val tracerProvider: TracerProvider,
) : PublisherProvider<T> {
    private val cacheMutex = Mutex()
    private val publisherCache: MutableMap<String, Publisher<T>> = mutableMapOf()

    override suspend fun publisher(topic: String): Publisher<T> {
        if (topic.isEmpty()) throw EmptyTopicNameException()
        return cacheMutex.withLock {
            publisherCache.getOrPut(topic) {
                RedisPublisher(
                    o11y = Observer("${topic}_publisher", logger, tracerProvider),
                    client = client,
                    encoder = encoder,
                    topic = topic,
                    circuitBreaker = circuitBreaker,
                )
            }
        }
    }

    override suspend fun ping() {
        client.ping()
    }

    override suspend fun close() {
        try {
            client.close()
        } catch (error: Throwable) {
            o11y.logger.error("closing redis publisher", error)
        }
    }
}

/**
 * Builds a Redis-backed [PublisherProvider]. Port of platform-go's `redis.ProvideRedisPublisherProvider`.
 *
 * @param config connection settings; [RedisMessageQueueConfig.validate] is applied up front.
 * @param encoder turns published values of type [T] into bytes, fixing [T] for every publisher this
 *   provider hands out. Serialization is an explicit boundary decision (see [MessageEncoder]);
 *   [ByteArrayMessageEncoder]/[StringMessageEncoder] are the ready-made raw encoders.
 * @param circuitBreaker optional breaker wrapping each publish; defaults to the always-closed noop.
 * @param client an override [RedisPubSubClient] (a fake in tests); when `null` a lazily-connecting
 *   [LettuceRedisPubSubClient] is built from [config].
 */
public fun <T : Any> redisPublisherProvider(
    config: RedisMessageQueueConfig,
    encoder: MessageEncoder<T>,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    client: RedisPubSubClient? = null,
): PublisherProvider<T> {
    config.validate()
    return RedisPublisherProvider(
        o11y = Observer(PUBLISHER_PROVIDER_NAME, logger, tracerProvider),
        client = client ?: LettuceRedisPubSubClient(config, logger),
        encoder = encoder,
        circuitBreaker = circuitBreaker,
        logger = logger,
        tracerProvider = tracerProvider,
    )
}

private const val PUBLISHER_PROVIDER_NAME = "redis_publisher_provider"

// platform-kt's observability Keys has no topic key (server-only keys were dropped when porting
// Keys.kt for Android); the backend records the topic under this literal, matching Go's keys.TopicKey.
internal const val TOPIC_KEY: String = "topic"
