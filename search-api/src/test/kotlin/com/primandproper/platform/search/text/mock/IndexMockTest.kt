package com.primandproper.platform.search.text.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the configurable text-index double, mirroring platform-go's moq-generated `IndexMock`. */
class IndexMockTest {
    @Test
    fun `configured func is invoked and the call is recorded`() =
        runTest {
            val mock = IndexMock<String>(searchFunc = { q -> listOf("hit-for-$q") })
            assertEquals(listOf("hit-for-k"), mock.search("k"))
            assertEquals(listOf("k"), mock.searchCalls)
        }

    @Test
    fun `an unset func throws when called`() =
        runTest {
            assertFailsWith<IllegalStateException> { IndexMock<String>().wipe() }
        }

    @Test
    fun `index records the id and value`() =
        runTest {
            val mock = IndexMock<String>(indexFunc = { _, _ -> })
            mock.index("id-1", "payload")
            assertEquals(listOf<Pair<String, Any>>("id-1" to "payload"), mock.indexCalls)
        }

    @Test
    fun `delete records the id`() =
        runTest {
            val mock = IndexMock<String>(deleteFunc = { })
            mock.delete("id-2")
            assertEquals(listOf("id-2"), mock.deleteCalls)
        }
}
