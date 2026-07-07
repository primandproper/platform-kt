package com.primandproper.platform.cache

import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of platform-go's `cache/memory/memory_test.go`. */
class InMemoryCacheTest {
    private data class Example(val name: String)

    private companion object {
        const val EXAMPLE_KEY = "example"
    }

    @Test
    fun `standard construction yields a non-null cache`() {
        val cache = InMemoryCache<Example>()
        assertTrue(cache is BatchCache<Example>)
    }

    @Test
    fun `Get returns a previously set value`() =
        runTest {
            val cache = InMemoryCache<Example>()
            val expected = Example("Get")
            cache.set(EXAMPLE_KEY, expected)

            assertEquals(expected, cache.get(EXAMPLE_KEY))
        }

    @Test
    fun `Get observes an operation with no errors`() =
        runTest {
            val obs = RecordingObserver()
            val cache = InMemoryCache<Example>(obs)

            val expected = Example("observes")
            cache.set(EXAMPLE_KEY, expected)
            assertEquals(expected, cache.get(EXAMPLE_KEY))

            // Assert the Get operation lifecycle: it opened, recorded the key, ended, no errors.
            val getOp = obs.operations.last { it.name == "Get" }
            assertTrue(getOp.ended)
            assertTrue(getOp.errors.isEmpty())
            assertEquals(EXAMPLE_KEY, getOp.values["name"])
        }

    @Test
    fun `Set stores into the backing map`() =
        runTest {
            val cache = InMemoryCache<Example>()
            assertEquals(0, cache.cache.size)
            cache.set(EXAMPLE_KEY, Example("Set"))
            assertEquals(1, cache.cache.size)
        }

    @Test
    fun `Delete removes from the backing map`() =
        runTest {
            val cache = InMemoryCache<Example>()
            assertEquals(0, cache.cache.size)
            cache.set(EXAMPLE_KEY, Example("Delete"))
            assertEquals(1, cache.cache.size)
            cache.delete(EXAMPLE_KEY)
            assertEquals(0, cache.cache.size)
        }

    @Test
    fun `GetMany returns only hits`() =
        runTest {
            val cache = InMemoryCache<Example>()
            val hit = Example("hit")
            cache.set("hit", hit)

            val out = cache.getMany(listOf("hit", "miss"))
            assertEquals(1, out.size)
            assertEquals(hit, out["hit"])
            assertNull(out["miss"])
        }

    @Test
    fun `GetMany with empty keys returns an empty map`() =
        runTest {
            val cache = InMemoryCache<Example>()
            assertEquals(emptyMap(), cache.getMany(emptyList()))
        }

    @Test
    fun `SetMany stores every item`() =
        runTest {
            val cache = InMemoryCache<Example>()
            assertEquals(0, cache.cache.size)
            cache.setMany(mapOf("a" to Example("a"), "b" to Example("b")))
            assertEquals(2, cache.cache.size)
        }

    @Test
    fun `Ping succeeds`() =
        runTest {
            InMemoryCache<Example>().ping()
        }
}
