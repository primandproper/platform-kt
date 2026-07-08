package com.primandproper.platform.search.text.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Port of platform-go's `search/text/noop/noop_test.go`. */
class NoopIndexTest {
    private data class Example(val name: String)

    @Test
    fun `Search returns an empty list`() =
        runTest {
            assertTrue(NoopIndex<Example>().search("anything").isEmpty())
        }

    @Test
    fun `Index is a no-op`() =
        runTest {
            NoopIndex<Example>().index("id", Example("doc"))
        }

    @Test
    fun `Delete is a no-op`() =
        runTest {
            NoopIndex<Example>().delete("id")
        }

    @Test
    fun `Wipe is a no-op`() =
        runTest {
            NoopIndex<Example>().wipe()
        }
}
