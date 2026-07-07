package com.primandproper.platform.cache.redis

import com.primandproper.platform.cache.StringCacheCodec
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Port of platform-go's `cache/redis/redis_test.go`, exercised against an in-memory fake client. */
class RedisCacheTest {
    private fun cache(
        client: FakeRedisClient = FakeRedisClient(),
        cluster: Boolean = false,
        observer: RecordingObserver? = null,
    ): RedisCache<String> =
        if (observer != null) {
            RedisCache(observer, client, StringCacheCodec, 1.hours, cluster)
        } else {
            RedisCache(client, StringCacheCodec, 1.hours, cluster)
        }

    @Test
    fun `Set then Get round-trips through the codec`() =
        runTest {
            val client = FakeRedisClient()
            val c = cache(client)
            c.set("k", "v")
            assertEquals("v", client.store["k"])
            assertEquals("v", c.get("k"))
        }

    @Test
    fun `Get returns null on a miss`() =
        runTest {
            assertNull(cache().get("absent"))
        }

    @Test
    fun `Get records the error on the span and rethrows`() =
        runTest {
            val obs = RecordingObserver()
            val c = cache(FakeRedisClient(failOn = "get"), observer = obs)

            assertFailsWith<RuntimeException> { c.get("k") }

            val getOp = obs.operations.last { it.name == "Get" }
            assertTrue(getOp.errors.isNotEmpty())
            assertTrue(getOp.ended)
        }

    @Test
    fun `Delete removes the key`() =
        runTest {
            val client = FakeRedisClient()
            val c = cache(client)
            c.set("k", "v")
            c.delete("k")
            assertNull(client.store["k"])
        }

    @Test
    fun `GetMany returns only hits`() =
        runTest {
            val client = FakeRedisClient()
            val c = cache(client)
            c.set("hit", "H")

            val out = c.getMany(listOf("hit", "miss"))
            assertEquals(mapOf("hit" to "H"), out)
        }

    @Test
    fun `GetMany with empty keys short-circuits`() =
        runTest {
            val client = FakeRedisClient()
            assertEquals(emptyMap(), cache(client).getMany(emptyList()))
            assertTrue(client.mgetCalls.isEmpty())
        }

    @Test
    fun `SetMany stores every item`() =
        runTest {
            val client = FakeRedisClient()
            cache(client).setMany(mapOf("a" to "1", "b" to "2"))
            assertEquals("1", client.store["a"])
            assertEquals("2", client.store["b"])
        }

    @Test
    fun `single-node batches all keys in one round trip`() =
        runTest {
            val client = FakeRedisClient()
            cache(client, cluster = false).getMany(listOf("a", "b", "c"))
            assertEquals(1, client.mgetCalls.size)
            assertEquals(listOf("a", "b", "c"), client.mgetCalls.first())
        }

    @Test
    fun `cluster mode buckets keys by slot across multiple round trips`() =
        runTest {
            val client = FakeRedisClient()
            // Hashtags force distinct slots, so a cluster client must issue more than one MGET.
            val keys = listOf("{a}1", "{a}2", "{b}1")
            cache(client, cluster = true).getMany(keys)

            // {a}1 and {a}2 share a slot; {b}1 is on its own — two buckets.
            assertEquals(2, client.mgetCalls.size)
            assertTrue(client.mgetCalls.any { it.toSet() == setOf("{a}1", "{a}2") })
            assertTrue(client.mgetCalls.any { it == listOf("{b}1") })
        }

    @Test
    fun `Ping succeeds`() =
        runTest {
            cache().ping()
        }
}
