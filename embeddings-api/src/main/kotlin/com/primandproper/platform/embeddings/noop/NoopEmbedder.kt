package com.primandproper.platform.embeddings.noop

import com.primandproper.platform.embeddings.Embedder
import com.primandproper.platform.embeddings.Embedding
import com.primandproper.platform.embeddings.EmbeddingInput
import java.time.Instant

/** The provider name a [NoopEmbedder] stamps on its results. Mirrors Go's `"noop"`. */
public const val NOOP_PROVIDER: String = "noop"

/**
 * An [Embedder] that returns an empty vector for every input — the port of platform-go's
 * `embeddings/noop.Embedder`. The safe default the config factory falls back to when no provider is
 * selected, and for tests that don't care about a real vector.
 *
 * The returned [Embedding] echoes the [EmbeddingInput.content] as its source text, stamps `"noop"`
 * for both model and provider, reports zero [Embedding.dimensions], and sets [Embedding.generatedAt]
 * — exactly the fields Go's noop populates.
 */
public class NoopEmbedder : Embedder {
    override suspend fun generateEmbedding(input: EmbeddingInput): Embedding =
        Embedding(
            vector = emptyList(),
            sourceText = input.content,
            model = NOOP_PROVIDER,
            provider = NOOP_PROVIDER,
            dimensions = 0,
            generatedAt = Instant.now(),
        )
}
