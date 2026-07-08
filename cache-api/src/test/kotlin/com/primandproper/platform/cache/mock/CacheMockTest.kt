package com.primandproper.platform.cache.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the configurable doubles, mirroring the contract of platform-go's moq-generated mocks. */
class CacheMockTest {
    @Test
    fun `configured func is invoked and the call is recorded`() =
        runTest {
            val mock = CacheMock<String>(getFunc = { key -> "value-for-$key" })
            assertEquals("value-for-k", mock.get("k"))
            assertEquals(listOf("k"), mock.getCalls)
        }

    @Test
    fun `an unset func throws when called`() =
        runTest {
            val mock = CacheMock<String>()
            assertFailsWith<IllegalStateException> { mock.get("k") }
        }

    @Test
    fun `batch mock records set and getMany calls`() =
        runTest {
            val mock =
                BatchCacheMock<String>(
                    setFunc = { _, _ -> },
                    getManyFunc = { keys -> keys.associateWith { "v-$it" } },
                )
            mock.set("a", "1")
            val out = mock.getMany(listOf("a", "b"))

            assertEquals(listOf("a" to "1"), mock.setCalls)
            assertEquals(listOf(listOf("a", "b")), mock.getManyCalls)
            assertEquals(mapOf("a" to "v-a", "b" to "v-b"), out)
        }
}
