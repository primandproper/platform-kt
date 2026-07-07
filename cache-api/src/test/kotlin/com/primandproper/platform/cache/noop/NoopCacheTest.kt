package com.primandproper.platform.cache.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of platform-go's `cache/noop/noop_test.go`. */
class NoopCacheTest {
    @Test
    fun `Get returns null on any key`() =
        runTest {
            assertNull(NoopCache<String>().get("any-key"))
        }

    @Test
    fun `Set is a no-op`() =
        runTest {
            NoopCache<String>().set("any-key", "value")
        }

    @Test
    fun `Delete is a no-op`() =
        runTest {
            NoopCache<String>().delete("any-key")
        }

    @Test
    fun `GetMany returns an empty map`() =
        runTest {
            val out = NoopCache<String>().getMany(listOf("a", "b"))
            assertTrue(out.isEmpty())
        }

    @Test
    fun `SetMany is a no-op`() =
        runTest {
            NoopCache<String>().setMany(mapOf("any-key" to "value"))
        }

    @Test
    fun `Ping is a no-op`() =
        runTest {
            NoopCache<String>().ping()
        }
}
