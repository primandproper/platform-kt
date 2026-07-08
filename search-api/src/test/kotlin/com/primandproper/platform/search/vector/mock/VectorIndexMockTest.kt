package com.primandproper.platform.search.vector.mock

import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.QueryResult
import com.primandproper.platform.search.vector.Vector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the configurable vector-index double, mirroring platform-go's moq-generated `IndexMock`. */
class VectorIndexMockTest {
    private data class Example(val name: String)

    @Test
    fun `configured func is invoked and the call is recorded`() =
        runTest {
            val mock = VectorIndexMock<Example>(queryFunc = { listOf(QueryResult("a", 0.1f)) })
            val req = QueryRequest(floatArrayOf(0.1f), topK = 5)
            assertEquals(listOf(QueryResult("a", 0.1f)), mock.query(req))
            assertEquals(listOf(req), mock.queryCalls)
        }

    @Test
    fun `an unset func throws when called`() =
        runTest {
            assertFailsWith<IllegalStateException> { VectorIndexMock<Example>().wipe() }
        }

    @Test
    fun `upsert records the vectors as a list`() =
        runTest {
            val mock = VectorIndexMock<Example>(upsertFunc = { })
            val v = Vector("id", floatArrayOf(0.1f), Example("d"))
            mock.upsert(v)
            assertEquals(listOf(listOf(v)), mock.upsertCalls)
        }

    @Test
    fun `delete records the ids as a list`() =
        runTest {
            val mock = VectorIndexMock<Example>(deleteFunc = { })
            mock.delete("a", "b")
            assertEquals(listOf(listOf("a", "b")), mock.deleteCalls)
        }
}
