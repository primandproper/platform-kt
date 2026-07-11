package com.primandproper.platform.llm.noop

import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.LlmProvider

/**
 * An [LlmProvider] that returns an empty [CompletionResult] for every request — the port of
 * platform-go's `llm/noop.Provider`. The safe default the config factory falls back to when no
 * provider is selected, and for tests that don't call a real model.
 */
public object NoopLlmProvider : LlmProvider {
    override suspend fun complete(params: CompletionParams): CompletionResult = CompletionResult()
}
