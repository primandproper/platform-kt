package com.primandproper.platform.messagequeue.mock

import com.primandproper.platform.messagequeue.Publisher
import com.primandproper.platform.messagequeue.PublisherProvider

/**
 * A configurable [Publisher] test double, mirroring platform-go's moq-generated `PublisherMock`. Each
 * method delegates to a settable `...Func`; calling a method whose `Func` was left `null` throws
 * [IllegalStateException] — the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. [publishCalls] / [publishAsyncCalls] record each call's argument and [closeCalls] counts
 * closes, standing in for moq's `XCalls()` accessors.
 */
public class PublisherMock<T : Any>(
    public var publishFunc: (suspend (T) -> Unit)? = null,
    public var publishAsyncFunc: (suspend (T) -> Unit)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : Publisher<T> {
    private val lock = Any()
    private val _publishCalls = mutableListOf<T>()
    private val _publishAsyncCalls = mutableListOf<T>()
    private var _closeCalls = 0

    public val publishCalls: List<T> get() = synchronized(lock) { _publishCalls.toList() }
    public val publishAsyncCalls: List<T> get() = synchronized(lock) { _publishAsyncCalls.toList() }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        requireFunc(closeFunc, "closeFunc").invoke()
    }

    override suspend fun publish(data: T) {
        synchronized(lock) { _publishCalls += data }
        requireFunc(publishFunc, "publishFunc").invoke(data)
    }

    override suspend fun publishAsync(data: T) {
        synchronized(lock) { _publishAsyncCalls += data }
        requireFunc(publishAsyncFunc, "publishAsyncFunc").invoke(data)
    }
}

/**
 * A configurable [PublisherProvider] test double, mirroring platform-go's moq-generated
 * `PublisherProviderMock`. Follows the same "null `Func` throws, calls are recorded" contract as
 * [PublisherMock].
 */
public class PublisherProviderMock<T : Any>(
    public var closeFunc: (() -> Unit)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
    public var publisherFunc: (suspend (String) -> Publisher<T>)? = null,
) : PublisherProvider<T> {
    private val lock = Any()
    private var _closeCalls = 0
    private var _pingCalls = 0
    private val _publisherCalls = mutableListOf<String>()

    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }
    public val pingCalls: Int get() = synchronized(lock) { _pingCalls }
    public val publisherCalls: List<String> get() = synchronized(lock) { _publisherCalls.toList() }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        requireFunc(closeFunc, "closeFunc").invoke()
    }

    override suspend fun ping() {
        synchronized(lock) { _pingCalls++ }
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun publisher(topic: String): Publisher<T> {
        synchronized(lock) { _publisherCalls += topic }
        return requireFunc(publisherFunc, "publisherFunc").invoke(topic)
    }
}
