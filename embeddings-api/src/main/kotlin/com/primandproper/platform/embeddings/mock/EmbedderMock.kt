package com.primandproper.platform.embeddings.mock

import com.primandproper.platform.embeddings.Embedder
import com.primandproper.platform.embeddings.Embedding
import com.primandproper.platform.embeddings.EmbeddingInput

/**
 * A configurable [Embedder] test double, mirroring platform-go's moq-generated
 * `mock.EmbedderMock`. [generateEmbedding] delegates to the settable [generateEmbeddingFunc]; calling
 * it while [generateEmbeddingFunc] is `null` throws [IllegalStateException], the same "unmocked call
 * surfaces immediately" behavior moq's generated panic gives. Every call's argument is recorded in
 * [generateEmbeddingCalls], standing in for moq's generated `GenerateEmbeddingCalls()` accessor.
 *
 * ```
 * val mock = EmbedderMock(generateEmbeddingFunc = { input -> /* build an Embedding */ })
 * ```
 */
public class EmbedderMock(
    public var generateEmbeddingFunc: (suspend (EmbeddingInput) -> Embedding)? = null,
) : Embedder {
    /** Every [EmbeddingInput] passed to [generateEmbedding], in order. */
    public val generateEmbeddingCalls: MutableList<EmbeddingInput> = mutableListOf()

    override suspend fun generateEmbedding(input: EmbeddingInput): Embedding {
        generateEmbeddingCalls += input
        val func = generateEmbeddingFunc ?: error("mock.generateEmbeddingFunc: method is null but was just called")
        return func(input)
    }
}
