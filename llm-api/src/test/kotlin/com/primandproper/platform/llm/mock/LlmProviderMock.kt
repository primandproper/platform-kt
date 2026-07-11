package com.primandproper.platform.llm.mock

import com.primandproper.platform.llm.CompletionChunk
import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.LlmProvider
import kotlinx.coroutines.flow.Flow

/**
 * A configurable [LlmProvider] test double, mirroring platform-go's moq-generated
 * `mock.ProviderMock`. [complete] delegates to the settable [completeFunc]; calling it while
 * [completeFunc] is `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's argument is recorded in
 * [completeCalls], standing in for moq's generated `CompletionCalls()` accessor.
 *
 * [stream] records into [streamCalls] and delegates to the settable [streamFunc]; leaving [streamFunc]
 * `null` falls through to [LlmProvider.stream]'s default (a single chunk built from [complete]), so a
 * test that only sets [completeFunc] still exercises streaming without extra wiring.
 *
 * ```
 * val mock = LlmProviderMock(completeFunc = { CompletionResult(content = "hi") })
 * ```
 */
public class LlmProviderMock(
    public var completeFunc: (suspend (CompletionParams) -> CompletionResult)? = null,
    public var streamFunc: ((CompletionParams) -> Flow<CompletionChunk>)? = null,
) : LlmProvider {
    private val lock = Any()
    private val _completeCalls = mutableListOf<CompletionParams>()
    private val _streamCalls = mutableListOf<CompletionParams>()

    /** Every [CompletionParams] passed to [complete], in order. */
    public val completeCalls: List<CompletionParams> get() = synchronized(lock) { _completeCalls.toList() }

    /** Every [CompletionParams] passed to [stream], in order. */
    public val streamCalls: List<CompletionParams> get() = synchronized(lock) { _streamCalls.toList() }

    override suspend fun complete(params: CompletionParams): CompletionResult {
        synchronized(lock) { _completeCalls += params }
        val func = completeFunc ?: error("mock.completeFunc: method is null but was just called")
        return func(params)
    }

    override fun stream(params: CompletionParams): Flow<CompletionChunk> {
        synchronized(lock) { _streamCalls += params }
        return streamFunc?.invoke(params) ?: super.stream(params)
    }
}
