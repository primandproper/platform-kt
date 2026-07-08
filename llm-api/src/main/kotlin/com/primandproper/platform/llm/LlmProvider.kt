package com.primandproper.platform.llm

/**
 * A language-model completion provider — the port of platform-go's `llm.Provider`.
 *
 * Go's method threads a `context.Context` and returns `(*CompletionResult, error)`; this port
 * suspends instead (cancellation and trace context ride the coroutine context) and signals failure
 * by throwing, the idiomatic Kotlin shape. Backends live in sibling modules (`:llm-anthropic`; an
 * OpenAI backend is a documented `TODO(openai)` seam); the noop and mock doubles ship here.
 */
public interface LlmProvider {
    /** Completes the chat described by [params], returning the model's reply or throwing on failure. */
    public suspend fun complete(params: CompletionParams): CompletionResult
}
