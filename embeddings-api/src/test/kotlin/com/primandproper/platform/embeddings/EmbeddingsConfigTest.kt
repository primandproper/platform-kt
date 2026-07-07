package com.primandproper.platform.embeddings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Mirrors platform-go's `embeddings/config/config_test.go` provider-validation cases. */
class EmbeddingsConfigTest {
    @Test
    fun `every known provider resolves from its value`() {
        assertEquals(EmbeddingProvider.OPENAI, EmbeddingProvider.fromValue("openai"))
        assertEquals(EmbeddingProvider.OLLAMA, EmbeddingProvider.fromValue("ollama"))
        assertEquals(EmbeddingProvider.COHERE, EmbeddingProvider.fromValue("cohere"))
    }

    @Test
    fun `fromValue trims and lowercases`() {
        assertEquals(EmbeddingProvider.OPENAI, EmbeddingProvider.fromValue("  OpenAI "))
    }

    @Test
    fun `fromValue returns null for an unknown provider`() {
        assertNull(EmbeddingProvider.fromValue("huggingface"))
    }

    @Test
    fun `validate accepts a known provider`() {
        EmbeddingsConfig(provider = "openai").validate()
    }

    @Test
    fun `validate rejects an unknown provider`() {
        assertFailsWith<InvalidEmbeddingProviderException> { EmbeddingsConfig(provider = "huggingface").validate() }
    }

    @Test
    fun `empty provider is permitted for noop fallback`() {
        EmbeddingsConfig(provider = "").validate()
    }
}
