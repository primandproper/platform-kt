package com.primandproper.platform.cache.redis

import com.primandproper.platform.cache.BatchCache
import com.primandproper.platform.cache.CacheCodec
import com.primandproper.platform.cache.CacheConfig
import com.primandproper.platform.cache.CacheProvider
import com.primandproper.platform.cache.InMemoryCache
import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.TracerProvider
import kotlin.time.Duration.Companion.hours

/**
 * Builds a [BatchCache] for the configured provider. Port of platform-go's `config.ProvideCache`.
 *
 * This factory lives in `:cache-redis` rather than `:cache-api` because it unites both backends — the
 * in-memory cache (from `:cache-api`) and the Redis cache — and wiring it in the API module would
 * force a dependency cycle. It is the analog of Go's `cache/config` package sitting above both
 * `cache/memory` and `cache/redis`.
 *
 * @param config the provider and expiry.
 * @param codec turns `T` into its stored string form; used only by the Redis backend, but required up
 *   front so the signature is provider-agnostic.
 * @param redisConfig required when [CacheConfig.provider] is [CacheProvider.REDIS]; ignored otherwise.
 * @param redisClient an override [RedisClient] (a fake in tests); when `null` a lazily-connecting
 *   [LettuceRedisClient] is built from [redisConfig].
 * @param circuitBreaker optional breaker threaded into the Redis backend; defaults to noop. Mirrors
 *   platform-go's `config.ProvideCache`, which builds a breaker from config and passes it to the cache.
 *   Ignored by the in-memory provider.
 */
public fun <T : Any> provideCache(
    config: CacheConfig,
    codec: CacheCodec<T>,
    redisConfig: RedisCacheConfig? = null,
    redisClient: RedisClient? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    circuitBreaker: CircuitBreaker? = null,
): BatchCache<T> =
    when (config.provider) {
        CacheProvider.MEMORY -> InMemoryCache(logger, tracerProvider)
        CacheProvider.REDIS -> {
            val redis = requireNotNull(redisConfig) { "redis provider requires a RedisCacheConfig" }
            redis.validate()
            // Go guards a non-positive expiry up to one hour; CacheConfig defaults to 1h, but honor
            // the same floor for an explicitly zero/negative value.
            val expiry = if (config.expiry.isPositive()) config.expiry else 1.hours
            RedisCache(
                client = redisClient ?: LettuceRedisClient(redis),
                codec = codec,
                expiration = expiry,
                cluster = redis.clusterMode(),
                logger = logger,
                tracerProvider = tracerProvider,
                circuitBreaker = circuitBreaker,
            )
        }
    }
