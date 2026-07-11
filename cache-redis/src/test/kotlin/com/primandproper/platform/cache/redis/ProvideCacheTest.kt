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
    fun `redis provider with cluster addresses is rejected until a cluster adapter lands`() {
        // The default standalone LettuceRedisClient cannot follow cluster redirects, so a multi-address
        // (cluster) config must fail loudly rather than silently pin to the first seed address.
        assertFailsWith<UnsupportedClusterConfigException> {
            provideCache(
                config = CacheConfig(CacheProvider.REDIS),
                codec = StringCacheCodec,
                redisConfig = RedisCacheConfig(listOf("localhost:6379", "localhost:6380")),
            )
        }
    }

    @Test
    fun `redis provider with the cluster flag is rejected`() {
        assertFailsWith<UnsupportedClusterConfigException> {
            provideCache(
                config = CacheConfig(CacheProvider.REDIS),
                codec = StringCacheCodec,
                redisConfig = RedisCacheConfig(listOf("localhost:6379"), cluster = true),
            )
        }
    }

    @Test
    fun `cluster config with an injected client is allowed`() {
        // An injected (cluster-aware) client is the escape hatch; only the standalone client is rejected.
        val c =
            provideCache(
                config = CacheConfig(CacheProvider.REDIS),
                codec = StringCacheCodec,
                redisConfig = RedisCacheConfig(listOf("localhost:6379", "localhost:6380")),
                redisClient = FakeRedisClient(),
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
