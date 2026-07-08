package com.primandproper.platform.embeddings

/**
 * The content to be embedded. Port of platform-go's `embeddings.Input`.
 *
 * [model] optionally overrides the provider's configured default model; leave it empty to use the
 * default from the backend's config (see `:embeddings-openai`'s `OpenAiConfig.defaultModel`).
 */
public data class EmbeddingInput(
    val content: String,
    val model: String = "",
)
