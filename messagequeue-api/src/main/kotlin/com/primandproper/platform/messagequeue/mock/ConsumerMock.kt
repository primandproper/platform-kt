package com.primandproper.platform.messagequeue.mock

import com.primandproper.platform.messagequeue.Consumer
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.ConsumerProvider

/**
 * A configurable [Consumer] test double, mirroring platform-go's moq-generated `ConsumerMock`.
 * [consume] delegates to a settable [consumeFunc]; a `null` func throws [IllegalStateException]. Each
 * call is recorded in [consumeCalls].
 */
public class ConsumerMock(
    public var consumeFunc: (suspend () -> Unit)? = null,
) : Consumer {
    public val consumeCalls: MutableList<Unit> = mutableListOf()

    override suspend fun consume() {
        consumeCalls += Unit
        requireFunc(consumeFunc, "consumeFunc").invoke()
    }
}

/**
 * A configurable [ConsumerProvider] test double, mirroring platform-go's moq-generated
 * `ConsumerProviderMock`. Follows the same "null `Func` throws, calls are recorded" contract as
 * [ConsumerMock].
 */
public class ConsumerProviderMock(
    public var closeFunc: (() -> Unit)? = null,
    public var provideConsumerFunc: (suspend (String, ConsumerHandler) -> Consumer)? = null,
) : ConsumerProvider {
    public val closeCalls: MutableList<Unit> = mutableListOf()
    public val provideConsumerCalls: MutableList<Pair<String, ConsumerHandler>> = mutableListOf()

    override fun close() {
        closeCalls += Unit
        requireFunc(closeFunc, "closeFunc").invoke()
    }

    override suspend fun provideConsumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer {
        provideConsumerCalls += topic to handler
        return requireFunc(provideConsumerFunc, "provideConsumerFunc").invoke(topic, handler)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
