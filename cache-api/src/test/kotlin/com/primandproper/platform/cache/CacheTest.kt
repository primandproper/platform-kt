package com.primandproper.platform.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Mirrors platform-go's `cache_test.go` (`TestErrNotFound`), adapted to the exception sentinel. */
class CacheTest {
    @Test
    fun `CacheNotFoundException carries the not found message`() {
        val err = CacheNotFoundException()
        assertNotNull(err)
        assertEquals("not found", err.message)
    }

    @Test
    fun `getOrThrow returns the value when present`() =
        runTest {
            val cache = InMemoryCache<String>()
            cache.set("k", "v")
            assertEquals("v", cache.getOrThrow("k"))
        }

    @Test
    fun `getOrThrow throws on a miss`() =
        runTest {
            val cache = InMemoryCache<String>()
            assertFailsWith<CacheNotFoundException> { cache.getOrThrow("absent") }
        }

    @Test
    fun `get returns null on a miss`() =
        runTest {
            val cache = InMemoryCache<String>()
            assertNull(cache.get("absent"))
        }
}
