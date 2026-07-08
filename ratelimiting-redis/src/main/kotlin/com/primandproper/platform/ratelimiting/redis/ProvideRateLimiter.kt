package com.primandproper.platform.ratelimiting.redis

import com.primandproper.platform.observability.Logger
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
 * [config] is normalized through [RateLimitingConfig.ensureDefaults] first, matching Go's
 * `cfg.EnsureDefaults()` call inside `ProvideRateLimiter`.
 *
 * @param redisConfig required when [RateLimitingConfig.provider] is [RateLimitingProvider.REDIS];
 *   ignored otherwise.
 * @param redisClient an override [RedisClient] (a fake in tests); when `null` a lazily-connecting
 *   [LettuceRedisClient] is built from [redisConfig].
 */
public fun provideRateLimiter(
    config: RateLimitingConfig,
    redisConfig: RedisRateLimitingConfig? = null,
    redisClient: RedisClient? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): RateLimiter {
    val cfg = config.ensureDefaults()
    return when (cfg.provider) {
        RateLimitingProvider.NOOP -> NoopRateLimiter()
        RateLimitingProvider.MEMORY -> InMemoryRateLimiter(cfg.requestsPerSec, cfg.burstSize, logger, tracerProvider)
        RateLimitingProvider.REDIS -> {
            val redis = requireNotNull(redisConfig) { "redis provider requires a RedisRateLimitingConfig" }
            redis.validate()
            RedisRateLimiter(
                client = redisClient ?: LettuceRedisClient(redis),
                requestsPerSec = cfg.requestsPerSec,
                burstSize = cfg.burstSize,
                logger = logger,
                tracerProvider = tracerProvider,
            )
        }
    }
}
