package com.primandproper.platform.search.vector.noop

import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.Vector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Port of platform-go's `search/vector/noop/noop_test.go`. */
class NoopVectorIndexTest {
    private data class Example(val name: String)

    @Test
    fun `Upsert is a no-op`() =
        runTest {
            NoopVectorIndex<Example>().upsert(
                Vector("abc", floatArrayOf(0.1f, 0.2f, 0.3f), Example("doc")),
            )
        }

    @Test
    fun `Delete is a no-op`() =
        runTest {
            NoopVectorIndex<Example>().delete("abc", "def")
        }

    @Test
    fun `Wipe is a no-op`() =
        runTest {
            NoopVectorIndex<Example>().wipe()
        }

    @Test
    fun `Query returns an empty result set`() =
        runTest {
            val results = NoopVectorIndex<Example>().query(QueryRequest(floatArrayOf(0.1f, 0.2f, 0.3f), topK = 10))
            assertTrue(results.isEmpty())
        }
}
