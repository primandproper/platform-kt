package com.primandproper.platform.messagequeue.noop

import com.primandproper.platform.messagequeue.Consumer
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.ConsumerProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A no-op [Consumer]: [consume] returns immediately and [messages] is an empty flow, so neither
 * delivers anything. Port of platform-go's `messagequeue/noop.consumer`, whose `Consume` is an empty
 * method.
 */
public object NoopConsumer : Consumer {
    override suspend fun consume() {}

    override fun messages(): Flow<ByteArray> = emptyFlow()
}

/**
 * A no-op [ConsumerProvider] handing out [NoopConsumer]s. Port of platform-go's
 * `messagequeue/noop.consumerProvider`.
 */
public object NoopConsumerProvider : ConsumerProvider {
    override suspend fun close() {}

    override suspend fun consumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer = NoopConsumer
}
