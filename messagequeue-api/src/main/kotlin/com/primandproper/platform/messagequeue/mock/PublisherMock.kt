package com.primandproper.platform.messagequeue.mock

import com.primandproper.platform.messagequeue.Publisher
import com.primandproper.platform.messagequeue.PublisherProvider

/**
 * A configurable [Publisher] test double, mirroring platform-go's moq-generated `PublisherMock`. Each
 * method delegates to a settable `...Func`; calling a method whose `Func` was left `null` throws
 * [IllegalStateException] — the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's arguments are recorded in the matching `...Calls` list, standing in for
 * moq's `XCalls()` accessors.
 */
public class PublisherMock(
    public var publishFunc: (suspend (Any) -> Unit)? = null,
    public var publishAsyncFunc: (suspend (Any) -> Unit)? = null,
    public var stopFunc: (() -> Unit)? = null,
) : Publisher {
    public val publishCalls: MutableList<Any> = mutableListOf()
    public val publishAsyncCalls: MutableList<Any> = mutableListOf()
    public val stopCalls: MutableList<Unit> = mutableListOf()

    override fun stop() {
        stopCalls += Unit
        requireFunc(stopFunc, "stopFunc").invoke()
    }

    override suspend fun publish(data: Any) {
        publishCalls += data
        requireFunc(publishFunc, "publishFunc").invoke(data)
    }

    override suspend fun publishAsync(data: Any) {
        publishAsyncCalls += data
        requireFunc(publishAsyncFunc, "publishAsyncFunc").invoke(data)
    }
}

/**
 * A configurable [PublisherProvider] test double, mirroring platform-go's moq-generated
 * `PublisherProviderMock`. Follows the same "null `Func` throws, calls are recorded" contract as
 * [PublisherMock].
 */
public class PublisherProviderMock(
    public var closeFunc: (() -> Unit)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
    public var providePublisherFunc: (suspend (String) -> Publisher)? = null,
) : PublisherProvider {
    public val closeCalls: MutableList<Unit> = mutableListOf()
    public val pingCalls: MutableList<Unit> = mutableListOf()
    public val providePublisherCalls: MutableList<String> = mutableListOf()

    override fun close() {
        closeCalls += Unit
        requireFunc(closeFunc, "closeFunc").invoke()
    }

    override suspend fun ping() {
        pingCalls += Unit
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun providePublisher(topic: String): Publisher {
        providePublisherCalls += topic
        return requireFunc(providePublisherFunc, "providePublisherFunc").invoke(topic)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
