package com.primandproper.platform.llm

/**
 * Parameters for a completion request — the port of platform-go's `llm.CompletionParams`.
 *
 * A `null` [model] (the default) lets the backend fall back to its configured default model (see
 * `:llm-anthropic`'s `AnthropicConfig.defaultModel`) — the Kotlin analog of Go's empty-string
 * fallback, with `null` rather than `""` denoting "unset".
 */
public data class CompletionParams(
    val messages: List<Message>,
    val model: String? = null,
)

/**
 * The token accounting a backend reported for a completion — the shared analog of the per-backend
 * usage shapes (e.g. `:llm-anthropic`'s `input_tokens`/`output_tokens`). Exposed on
 * [CompletionResult] and the terminal [CompletionChunk] so callers can read usage directly; the same
 * total is also recorded on the span under [LlmKeys.TOTAL_TOKENS].
 */
public data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
) {
    /** Input + output tokens — the value recorded under [LlmKeys.TOTAL_TOKENS]. */
    public val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * The result of a completion request — the port of platform-go's `llm.CompletionResult`. Carries the
 * assistant's reply as flat [content] text, plus the [usage] and [finishReason] the backend reported
 * (both `null` when the backend does not report them — e.g. the noop backend, which returns an empty
 * result). The usage/finish-reason keys already existed on [LlmKeys] but were unreachable by callers;
 * surfacing them here lets a caller read token accounting and the stop reason from the return value.
 */
public data class CompletionResult(
    val content: String = "",
    val usage: TokenUsage? = null,
    val finishReason: String? = null,
)

/**
 * One chunk of a streamed completion (see [LlmProvider.stream]). [content] is the incremental text
 * delta produced since the previous chunk. [usage] and [finishReason] are terminal metadata: a
 * backend populates them only on the final chunk (and leaves them `null` on intermediate chunks),
 * mirroring how a streaming API delivers token accounting and the stop reason at the end of a stream.
 *
 * The default [LlmProvider.stream] emits a single chunk carrying the whole [CompletionResult.content]
 * plus its terminal [usage]/[finishReason]; a backend with true server-sent streaming overrides
 * [LlmProvider.stream] to emit many text-delta chunks followed by a terminal chunk.
 */
public data class CompletionChunk(
    val content: String = "",
    val usage: TokenUsage? = null,
    val finishReason: String? = null,
)
