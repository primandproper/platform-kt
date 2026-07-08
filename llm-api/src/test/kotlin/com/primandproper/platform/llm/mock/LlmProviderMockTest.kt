package com.primandproper.platform.llm.mock

import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.Message
import com.primandproper.platform.llm.Role
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Mirrors platform-go's moq-generated `mock.ProviderMock` behavior. */
class LlmProviderMockTest {
    private val params = CompletionParams(model = "m", messages = listOf(Message(Role.USER, "hi")))

    @Test
    fun `complete delegates to the configured func and records the call`() =
        runTest {
            val mock = LlmProviderMock(completeFunc = { CompletionResult(content = "pong") })

            val result = mock.complete(params)

            assertEquals("pong", result.content)
            assertEquals(1, mock.completeCalls.size)
            assertEquals(params, mock.completeCalls.single())
        }

    @Test
    fun `complete throws when no func is configured`() =
        runTest {
            assertFailsWith<IllegalStateException> { LlmProviderMock().complete(params) }
        }
}
