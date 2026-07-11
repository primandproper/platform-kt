package com.primandproper.platform.embeddings.noop

import com.primandproper.platform.embeddings.EmbeddingInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mirrors platform-go's `embeddings/noop/noop_test.go`. */
class NoopEmbedderTest {
    @Test
    fun `generateEmbedding returns an empty vector with noop provenance`() =
        runTest {
            val result = NoopEmbedder.generateEmbedding(EmbeddingInput(content = "hello world"))

            assertTrue(result.vector.isEmpty())
            assertEquals(0, result.dimensions)
            assertEquals("hello world", result.sourceText)
            assertEquals("noop", result.model)
            assertEquals("noop", result.provider)
        }
}
