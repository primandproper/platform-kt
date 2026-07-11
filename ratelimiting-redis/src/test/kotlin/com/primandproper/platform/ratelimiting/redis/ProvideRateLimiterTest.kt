package com.primandproper.platform.ratelimiting.redis

import com.primandproper.platform.ratelimiting.InMemoryRateLimiter
import com.primandproper.platform.ratelimiting.RateLimitingConfig
import com.primandproper.platform.ratelimiting.RateLimitingProvider
import com.primandproper.platform.ratelimiting.noop.NoopRateLimiter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `config/config_test.go` (`TestConfig_ProvideRateLimiter`). */
class ProvideRateLimiterTest {
    @Test
    fun `noop provider builds a noop limiter`() =
        runTest {
            val rl = RateLimiter(RateLimitingConfig(provider = RateLimitingProvider.NOOP))
            assertTrue(rl is NoopRateLimiter)
            assertTrue(rl.allow("x"))
        }

    @Test
    fun `default provider builds a noop limiter`() =
        runTest {
            // The RateLimitingConfig default provider is NOOP — Go treats a blank provider the same way.
            val rl = RateLimiter(RateLimitingConfig())
            assertTrue(rl is NoopRateLimiter)
            assertTrue(rl.allow("x"))
        }

    @Test
    fun `memory provider builds an in-memory limiter that throttles`() =
        runTest {
            val rl =
                RateLimiter(
                    RateLimitingConfig(provider = RateLimitingProvider.MEMORY, requestsPerSec = 1.0, burstSize = 1),
                )
            assertTrue(rl is InMemoryRateLimiter)
            assertTrue(rl.allow("x"))
            assertFalse(rl.allow("x"))
        }

    @Test
    fun `redis provider builds a redis limiter without connecting`() {
        val rl =
            RateLimiter(
                config = RateLimitingConfig(provider = RateLimitingProvider.REDIS),
                redisConfig = RedisRateLimitingConfig(listOf("localhost:6379")),
            )
        assertTrue(rl is RedisRateLimiter)
    }

    @Test
    fun `redis provider drives an injected client`() =
        runTest {
            val client = FakeRedisClient(result = 1L)
            val rl =
                RateLimiter(
                    config = RateLimitingConfig(provider = RateLimitingProvider.REDIS, requestsPerSec = 1.0, burstSize = 1),
                    redisConfig = RedisRateLimitingConfig(listOf("localhost:6379")),
                    redisClient = client,
                )
            assertTrue(rl.allow("x"))
            assertTrue(client.evalCalls.isNotEmpty())
        }

    @Test
    fun `redis provider missing config fails`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimiter(RateLimitingConfig(provider = RateLimitingProvider.REDIS))
        }
    }
}
