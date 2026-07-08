package com.primandproper.platform.cache.redis

import com.primandproper.platform.cache.CacheConfig
import com.primandproper.platform.cache.CacheProvider
import com.primandproper.platform.cache.InMemoryCache
import com.primandproper.platform.cache.StringCacheCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `cache/config/config_test.go` (`TestProvideCache`). */
class ProvideCacheTest {
    @Test
    fun `memory provider builds an in-memory cache`() {
        val c = provideCache(CacheConfig(CacheProvider.MEMORY), StringCacheCodec)
        assertTrue(c is InMemoryCache<String>)
    }

    @Test
    fun `redis provider builds a redis cache without connecting`() {
        val c =
            provideCache(
                config = CacheConfig(CacheProvider.REDIS),
                codec = StringCacheCodec,
                redisConfig = RedisCacheConfig(listOf("localhost:6379")),
            )
        assertTrue(c is RedisCache<String>)
    }

    @Test
    fun `redis provider with cluster addresses builds a redis cache`() {
        val c =
            provideCache(
                config = CacheConfig(CacheProvider.REDIS),
                codec = StringCacheCodec,
                redisConfig = RedisCacheConfig(listOf("localhost:6379", "localhost:6380")),
            )
        assertTrue(c is RedisCache<String>)
    }

    @Test
    fun `redis provider missing config fails`() {
        assertFailsWith<IllegalArgumentException> {
            provideCache(CacheConfig(CacheProvider.REDIS), StringCacheCodec)
        }
    }

    @Test
    fun `redis provider drives an injected client`() =
        runTest {
            val client = FakeRedisClient()
            val c =
                provideCache(
                    config = CacheConfig(CacheProvider.REDIS),
                    codec = StringCacheCodec,
                    redisConfig = RedisCacheConfig(listOf("localhost:6379")),
                    redisClient = client,
                )
            c.set("k", "v")
            assertEquals("v", c.get("k"))
        }
}
