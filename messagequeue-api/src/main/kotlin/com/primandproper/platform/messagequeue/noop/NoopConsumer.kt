package com.primandproper.platform.messagequeue.noop

import com.primandproper.platform.messagequeue.Consumer
import com.primandproper.platform.messagequeue.ConsumerHandler
import com.primandproper.platform.messagequeue.ConsumerProvider

/**
 * A no-op [Consumer]: [consume] returns immediately without delivering anything. Port of platform-go's
 * `messagequeue/noop.consumer`, whose `Consume` is an empty method.
 */
public class NoopConsumer : Consumer {
    override suspend fun consume() {}
}

/**
 * A no-op [ConsumerProvider] handing out [NoopConsumer]s. Port of platform-go's
 * `messagequeue/noop.consumerProvider`.
 */
public class NoopConsumerProvider : ConsumerProvider {
    override fun close() {}

    override suspend fun provideConsumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer = NoopConsumer()
}
