package com.primandproper.platform.ratelimiting.redis

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.ratelimiting.InMemoryRateLimiter
import com.primandproper.platform.ratelimiting.RateLimiter
import com.primandproper.platform.ratelimiting.RateLimitingConfig
import com.primandproper.platform.ratelimiting.RateLimitingProvider
import com.primandproper.platform.ratelimiting.noop.NoopRateLimiter

/**
 * Builds a [RateLimiter] for the configured provider. Port of platform-go's
 * `ratelimitingcfg.Config.ProvideRateLimiter` (and `ProvideRateLimiterFromConfig`).
 *
 * This factory lives in `:ratelimiting-redis` rather than `:ratelimiting-api` because it unites all
 * three backends — noop and in-memory (from `:ratelimiting-api`) and the Redis limiter — and wiring it
 * in the API module would force a dependency cycle. It is the analog of Go's `ratelimiting/config`
 * package sitting above `ratelimiting`, `ratelimiting/noop`, and `ratelimiting/redis`.
 *
 * [config] arrives already defaulted and validated at construction (the analog of Go's
 * `cfg.EnsureDefaults()` call inside `ProvideRateLimiter`), so this factory consumes it directly.
 *
 * @param redisConfig required when [RateLimitingConfig.provider] is [RateLimitingProvider.REDIS];
 *   ignored otherwise.
 * @param redisClient an override [RedisClient] (a fake in tests); when `null` a lazily-connecting
 *   [LettuceRedisClient] is built from [redisConfig].
 */
public fun RateLimiter(
    config: RateLimitingConfig,
    redisConfig: RedisRateLimitingConfig? = null,
    redisClient: RedisClient? = null,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): RateLimiter {
    return when (config.provider) {
        RateLimitingProvider.NOOP -> NoopRateLimiter
        RateLimitingProvider.MEMORY -> InMemoryRateLimiter(config.requestsPerSec, config.burstSize, logger, tracerProvider)
        RateLimitingProvider.REDIS -> {
            val redis = requireNotNull(redisConfig) { "redis provider requires a RedisRateLimitingConfig" }
            redis.validate()
            RedisRateLimiter(
                client = redisClient ?: LettuceRedisClient(redis),
                requestsPerSec = config.requestsPerSec,
                burstSize = config.burstSize,
                logger = logger,
                tracerProvider = tracerProvider,
            )
        }
    }
}
