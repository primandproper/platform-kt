package com.primandproper.platform.messagequeue

/**
 * Handles a consumed message's raw payload. Port of platform-go's `messagequeue.ConsumerFunc`
 * (`func(context.Context, []byte) error`): Go threads a context and returns an error; this port
 * suspends and signals failure by throwing. A thrown exception is recorded by the [Consumer] and does
 * not stop the consume loop, matching Go — which delivers the handler error on its error channel and
 * keeps consuming.
 */
public fun interface ConsumerHandler {
    public suspend fun handle(message: ByteArray)
}

/**
 * Consumes messages from a queue, applying the handler supplied at [ConsumerProvider.provideConsumer]
 * time to each payload. Port of platform-go's `messagequeue.Consumer`.
 *
 * Go's `Consume(ctx, stopChan, errors)` blocks in a loop until the context is cancelled or a value is
 * sent on `stopChan`, delivering handler errors on the `errors` channel. This port folds all three Go
 * channels into coroutine idioms: [consume] is a `suspend` function that runs until the collecting
 * coroutine is **cancelled** (replacing both `ctx.Done()` and `stopChan`), and a handler failure is
 * recorded on the consume span (Go's `op.Acknowledge`) without halting the loop.
 */
public interface Consumer {
    /**
     * Subscribes and drives the handler over each delivered message until cancelled. Suspends for the
     * lifetime of the subscription; cancel the calling coroutine (e.g. cancel the `Job` from
     * `launch { consumer.consume() }`) to stop and release the subscription.
     */
    public suspend fun consume()
}

/**
 * Provides a [Consumer] for a given topic, caching one per topic. Port of platform-go's
 * `messagequeue.ConsumerProvider`.
 */
public interface ConsumerProvider {
    /** Releases the provider's resources — the shared client. */
    public fun close()

    /**
     * Returns a [Consumer] for [topic] that drives [handler] over each message, throwing
     * [EmptyTopicNameException] when [topic] is blank. Repeated calls for the same topic return the
     * same cached instance.
     */
    public suspend fun provideConsumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer
}
