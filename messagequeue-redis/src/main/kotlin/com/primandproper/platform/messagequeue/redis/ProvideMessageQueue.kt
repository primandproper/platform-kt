package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.messagequeue.ConsumerProvider
import com.primandproper.platform.messagequeue.MessageEncoder
import com.primandproper.platform.messagequeue.MessageQueueProvider
import com.primandproper.platform.messagequeue.PublisherProvider
import com.primandproper.platform.messagequeue.RawMessageEncoder
import com.primandproper.platform.messagequeue.noop.NoopConsumerProvider
import com.primandproper.platform.messagequeue.noop.NoopPublisherProvider
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.TracerProvider

/**
 * Builds a [PublisherProvider] for the configured [provider]. Port of platform-go's
 * `msgconfig.ProvidePublisherProvider`.
 *
 * This factory lives in `:messagequeue-redis` rather than `:messagequeue-api` because it unites the
 * Redis backend with the noop fallback — exactly as `:cache-redis`'s `provideCache` unites the Redis
 * and in-memory backends — and wiring it in the API module would force a dependency cycle. It is the
 * analog of Go's `messagequeue/config` package sitting above every backend.
 *
 * Only the Redis backend is ported; every other provider is a named seam that currently falls back to
 * the noop provider (a warning-worthy misconfiguration, never a crash), matching how Go logs
 * "Using noop publisher provider" for an unrecognized provider:
 * - TODO(sqs): platform-go's `messagequeue/sqs` (Amazon SQS via the AWS SDK).
 * - TODO(pubsub): platform-go's `messagequeue/pubsub` (GCP Pub/Sub).
 * - TODO(kafka): platform-go's `messagequeue/kafka` (segmentio/kafka-go).
 *
 * @param provider the selected provider; `null` (an unrecognized name) falls back to noop.
 * @param redisConfig required when [provider] is [MessageQueueProvider.REDIS]; ignored otherwise.
 * @param encoder the byte encoder for the Redis backend; defaults to [RawMessageEncoder].
 * @param circuitBreaker optional breaker threaded into the Redis backend; defaults to noop.
 * @param redisClient an override [RedisPubSubClient] (a fake in tests) for the Redis backend.
 */
public fun providePublisherProvider(
    provider: MessageQueueProvider?,
    redisConfig: RedisMessageQueueConfig? = null,
    encoder: MessageEncoder = RawMessageEncoder,
    circuitBreaker: CircuitBreaker? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    redisClient: RedisPubSubClient? = null,
): PublisherProvider =
    when (provider) {
        MessageQueueProvider.REDIS -> {
            val cfg = requireNotNull(redisConfig) { "redis provider requires a RedisMessageQueueConfig" }
            provideRedisPublisherProvider(cfg, encoder, circuitBreaker, logger, tracerProvider, redisClient)
        }
        // TODO(sqs), TODO(pubsub), TODO(kafka): not yet ported — fall back to noop, as Go does for an
        // unrecognized provider.
        MessageQueueProvider.SQS,
        MessageQueueProvider.PUBSUB,
        MessageQueueProvider.KAFKA,
        null,
        -> NoopPublisherProvider()
    }

/**
 * Builds a [ConsumerProvider] for the configured [provider]. Port of platform-go's
 * `msgconfig.ProvideConsumerProvider`. Same backend-selection rules and seams as
 * [providePublisherProvider].
 */
public fun provideConsumerProvider(
    provider: MessageQueueProvider?,
    redisConfig: RedisMessageQueueConfig? = null,
    circuitBreaker: CircuitBreaker? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    redisClient: RedisPubSubClient? = null,
): ConsumerProvider =
    when (provider) {
        MessageQueueProvider.REDIS -> {
            val cfg = requireNotNull(redisConfig) { "redis provider requires a RedisMessageQueueConfig" }
            provideRedisConsumerProvider(cfg, circuitBreaker, logger, tracerProvider, redisClient)
        }
        MessageQueueProvider.SQS,
        MessageQueueProvider.PUBSUB,
        MessageQueueProvider.KAFKA,
        null,
        -> NoopConsumerProvider()
    }
