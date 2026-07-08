package com.primandproper.platform.llm.mock

import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.LlmProvider

/**
 * A configurable [LlmProvider] test double, mirroring platform-go's moq-generated
 * `mock.ProviderMock`. [complete] delegates to the settable [completeFunc]; calling it while
 * [completeFunc] is `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's argument is recorded in
 * [completeCalls], standing in for moq's generated `CompletionCalls()` accessor.
 *
 * ```
 * val mock = LlmProviderMock(completeFunc = { CompletionResult(content = "hi") })
 * ```
 */
public class LlmProviderMock(
    public var completeFunc: (suspend (CompletionParams) -> CompletionResult)? = null,
) : LlmProvider {
    /** Every [CompletionParams] passed to [complete], in order. */
    public val completeCalls: MutableList<CompletionParams> = mutableListOf()

    override suspend fun complete(params: CompletionParams): CompletionResult {
        completeCalls += params
        val func = completeFunc ?: error("mock.completeFunc: method is null but was just called")
        return func(params)
    }
}
