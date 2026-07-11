package com.primandproper.platform.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A language-model completion provider — the port of platform-go's `llm.Provider`.
 *
 * Go's method threads a `context.Context` and returns `(*CompletionResult, error)`; this port
 * suspends instead (cancellation and trace context ride the coroutine context) and signals failure
 * by throwing, the idiomatic Kotlin shape. Backends live in sibling modules (`:llm-anthropic`; an
 * OpenAI backend is a documented `TODO(openai)` seam); the noop and mock doubles ship here.
 *
 * [complete] is the single abstract method, so [LlmProvider] is a `fun interface` — a lambda returning
 * a [CompletionResult] converts to a provider. [stream] is additive and ships with a default that
 * emits the non-streaming [complete] result as a single [CompletionChunk]; a backend that supports
 * true server-sent streaming overrides it to emit incremental text chunks. This keeps [complete] the
 * sole abstract method (every provider gets working streaming for free) while leaving real streaming
 * an opt-in override.
 */
public fun interface LlmProvider {
    /** Completes the chat described by [params], returning the model's reply or throwing on failure. */
    public suspend fun complete(params: CompletionParams): CompletionResult

    /**
     * Streams the completion described by [params] as a cold [Flow] of [CompletionChunk]s. The default
     * implementation delegates to [complete] and emits a single terminal chunk carrying the whole
     * reply plus its [CompletionResult.usage] and [CompletionResult.finishReason]; the [complete] call
     * (and any failure it throws) happens when the flow is collected. Backends with native streaming
     * override this to emit many text-delta chunks followed by a terminal usage/finish-reason chunk.
     */
    public fun stream(params: CompletionParams): Flow<CompletionChunk> =
        flow {
            val result = complete(params)
            emit(
                CompletionChunk(
                    content = result.content,
                    usage = result.usage,
                    finishReason = result.finishReason,
                ),
            )
        }
}
