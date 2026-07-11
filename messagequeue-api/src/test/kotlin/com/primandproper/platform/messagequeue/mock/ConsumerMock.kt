package com.primandproper.platform.messagequeue.mock

import com.primandproper.platform.messagequeue.Consumer
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.ConsumerProvider
import kotlinx.coroutines.flow.Flow

/**
 * A configurable [Consumer] test double, mirroring platform-go's moq-generated `ConsumerMock`.
 * [consume] delegates to a settable [consumeFunc] and [messages] to a settable [messagesFunc]; a `null`
 * func throws [IllegalStateException]. [consumeCalls] / [messagesCalls] count how often each was invoked.
 */
public class ConsumerMock(
    public var consumeFunc: (suspend () -> Unit)? = null,
    public var messagesFunc: (() -> Flow<ByteArray>)? = null,
) : Consumer {
    private val lock = Any()
    private var _consumeCalls = 0
    private var _messagesCalls = 0

    public val consumeCalls: Int get() = synchronized(lock) { _consumeCalls }
    public val messagesCalls: Int get() = synchronized(lock) { _messagesCalls }

    override suspend fun consume() {
        synchronized(lock) { _consumeCalls++ }
        requireFunc(consumeFunc, "consumeFunc").invoke()
    }

    override fun messages(): Flow<ByteArray> {
        synchronized(lock) { _messagesCalls++ }
        return requireFunc(messagesFunc, "messagesFunc").invoke()
    }
}

/**
 * A configurable [ConsumerProvider] test double, mirroring platform-go's moq-generated
 * `ConsumerProviderMock`. Follows the same "null `Func` throws, calls are recorded" contract as
 * [ConsumerMock].
 */
public class ConsumerProviderMock(
    public var closeFunc: (() -> Unit)? = null,
    public var consumerFunc: (suspend (String, ConsumerHandler) -> Consumer)? = null,
) : ConsumerProvider {
    private val lock = Any()
    private var _closeCalls = 0
    private val _consumerCalls = mutableListOf<Pair<String, ConsumerHandler>>()

    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }
    public val consumerCalls: List<Pair<String, ConsumerHandler>>
        get() = synchronized(lock) { _consumerCalls.toList() }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        requireFunc(closeFunc, "closeFunc").invoke()
    }

    override suspend fun consumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer {
        synchronized(lock) { _consumerCalls += topic to handler }
        return requireFunc(consumerFunc, "consumerFunc").invoke(topic, handler)
    }
}

/** Shared helper for the recording `messagequeue.mock` doubles: throws if a stub `Func` is unset. */
internal fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
