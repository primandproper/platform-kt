package com.primandproper.platform.embeddings.mock

import com.primandproper.platform.embeddings.Embedding
import com.primandproper.platform.embeddings.EmbeddingInput
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Mirrors the contract of platform-go's moq-generated `mock.EmbedderMock`. */
class EmbedderMockTest {
    private fun embedding() =
        Embedding(
            vector = floatArrayOf(0.1f, 0.2f),
            sourceText = "hi",
            model = "m",
            provider = "p",
            dimensions = 2,
            generatedAt = Instant.now(),
        )

    @Test
    fun `delegates to generateEmbeddingFunc and records the call`() =
        runTest {
            val seen = mutableListOf<EmbeddingInput>()
            val stub = embedding()
            val mock =
                EmbedderMock(generateEmbeddingFunc = {
                    seen += it
                    stub
                })

            val input = EmbeddingInput(content = "hi")
            val result = mock.generateEmbedding(input)

            assertEquals(stub, result)
            assertEquals(listOf(input), seen)
            assertEquals(listOf(input), mock.generateEmbeddingCalls)
        }

    @Test
    fun `calling with a null func throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { EmbedderMock().generateEmbedding(EmbeddingInput(content = "x")) }
        }
}
