package com.primandproper.platform.llm.mock

import com.primandproper.platform.llm.CompletionChunk
import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.Message
import com.primandproper.platform.llm.Role
import com.primandproper.platform.llm.TokenUsage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

    @Test
    fun `stream delegates to the configured streamFunc and records the call`() =
        runTest {
            val chunk = CompletionChunk(content = "delta", finishReason = "end_turn")
            val mock = LlmProviderMock(streamFunc = { flowOf(chunk) })

            val chunks = mock.stream(params).toList()

            assertEquals(listOf(chunk), chunks)
            assertEquals(params, mock.streamCalls.single())
        }

    @Test
    fun `stream falls back to the default single-chunk delegation over complete`() =
        runTest {
            val result = CompletionResult(content = "pong", usage = TokenUsage(3, 2), finishReason = "end_turn")
            val mock = LlmProviderMock(completeFunc = { result })

            val chunks = mock.stream(params).toList()

            assertEquals(1, chunks.size)
            assertEquals(CompletionChunk("pong", TokenUsage(3, 2), "end_turn"), chunks.single())
            // The default stream delegates to complete, so the call is recorded on both accessors.
            assertEquals(params, mock.streamCalls.single())
            assertEquals(params, mock.completeCalls.single())
        }
}
