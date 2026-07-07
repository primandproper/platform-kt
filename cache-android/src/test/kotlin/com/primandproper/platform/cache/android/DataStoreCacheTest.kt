package com.primandproper.platform.cache.android

import com.primandproper.platform.cache.StringCacheCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class DataStoreCacheTest {
    private fun cache(
        now: () -> Long = { 0L },
        ttl: Duration = 1.hours,
    ): DataStoreCache<String> = DataStoreCache(FakeDataStore(), StringCacheCodec, ttl, now)

    @Test
    fun `Set then Get round-trips`() =
        runTest {
            val c = cache()
            c.set("k", "v")
            assertEquals("v", c.get("k"))
        }

    @Test
    fun `Get returns null on a miss`() =
        runTest {
            assertNull(cache().get("absent"))
        }

    @Test
    fun `Delete removes a value`() =
        runTest {
            val c = cache()
            c.set("k", "v")
            c.delete("k")
            assertNull(c.get("k"))
        }

    @Test
    fun `an entry past its TTL reads as a miss`() =
        runTest {
            var now = 0L
            val c = cache(now = { now }, ttl = 10.milliseconds)
            c.set("k", "v")
            now = 5 // still fresh
            assertEquals("v", c.get("k"))
            now = 20 // past expiry
            assertNull(c.get("k"))
        }

    @Test
    fun `a non-positive TTL never expires`() =
        runTest {
            var now = 0L
            val c = cache(now = { now }, ttl = Duration.ZERO)
            c.set("k", "v")
            now = Long.MAX_VALUE
            assertEquals("v", c.get("k"))
        }

    @Test
    fun `GetMany returns only fresh hits`() =
        runTest {
            val c = cache()
            c.set("a", "1")
            c.set("b", "2")
            assertEquals(mapOf("a" to "1", "b" to "2"), c.getMany(listOf("a", "b", "missing")))
        }

    @Test
    fun `SetMany stores every item`() =
        runTest {
            val c = cache()
            c.setMany(mapOf("a" to "1", "b" to "2"))
            assertEquals("1", c.get("a"))
            assertEquals("2", c.get("b"))
        }

    @Test
    fun `Ping succeeds`() =
        runTest {
            cache().ping()
        }
}
