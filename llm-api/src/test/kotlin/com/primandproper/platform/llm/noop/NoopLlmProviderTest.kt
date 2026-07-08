package com.primandproper.platform.llm.noop

import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.Message
import com.primandproper.platform.llm.Role
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Mirrors platform-go's `llm/noop.noop_test.go`. */
class NoopLlmProviderTest {
    @Test
    fun `complete returns an empty result and does not throw`() =
        runTest {
            val provider = NoopLlmProvider()

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
}
