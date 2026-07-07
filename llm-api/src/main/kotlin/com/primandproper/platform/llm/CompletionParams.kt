package com.primandproper.platform.llm

/**
 * Parameters for a completion request — the port of platform-go's `llm.CompletionParams`.
 *
 * A blank [model] lets the backend fall back to its configured default model (see
 * `:llm-anthropic`'s `AnthropicConfig.defaultModel`), mirroring Go's empty-string fallback.
 */
public data class CompletionParams(
    val messages: List<Message>,
    val model: String = "",
)

/**
 * The result of a completion request — the port of platform-go's `llm.CompletionResult`. Carries the
 * assistant's reply as flat [content] text; the noop backend returns an empty string.
 */
public data class CompletionResult(
    val content: String = "",
)
