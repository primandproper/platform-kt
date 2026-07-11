package com.primandproper.platform.llm.noop

import com.primandproper.platform.llm.CompletionChunk
import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.Message
import com.primandproper.platform.llm.Role
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Mirrors platform-go's `llm/noop.noop_test.go`. */
class NoopLlmProviderTest {
    @Test
    fun `complete returns an empty result and does not throw`() =
        runTest {
            val provider = NoopLlmProvider

            val result =
                provider.complete(
                    CompletionParams(
                        model = "test",
                        messages = listOf(Message(Role.USER, "hello")),
                    ),
                )

            assertNotNull(result)
            assertEquals("", result.content)
        }

    @Test
    fun `stream emits a single empty chunk via the default delegation`() =
        runTest {
            val provider = NoopLlmProvider

            val chunks =
                provider
                    .stream(CompletionParams(messages = listOf(Message(Role.USER, "hello"))))
                    .toList()

            assertEquals(listOf(CompletionChunk(content = "")), chunks)
        }
}
