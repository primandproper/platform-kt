package com.primandproper.platform.embeddings

/**
 * A service that turns text into a vector embedding — the port of platform-go's `embeddings.Embedder`.
 *
 * Go's method threads a `context.Context` and returns `(*Embedding, error)`; this port suspends
 * instead (cancellation and trace context ride the coroutine context) and signals failure by
 * throwing, the idiomatic Kotlin shape. Backends live in sibling modules (`:embeddings-openai`); the
 * noop and mock doubles ship here.
 *
 * Note on nil input: Go's `GenerateEmbedding` takes a `*embeddings.Input` and returns
 * `ErrNilInput` when handed `nil`. This port makes [EmbeddingInput] a non-null parameter, so the
 * nil-input case is unrepresentable at the type level and `ErrNilInput` has no analog — the same
 * simplification `:email-api` makes with its non-null `OutboundEmailMessage`.
 */
public fun interface Embedder {
    /** Generates a vector [Embedding] for [input], throwing on failure. */
    public suspend fun generateEmbedding(input: EmbeddingInput): Embedding
}
