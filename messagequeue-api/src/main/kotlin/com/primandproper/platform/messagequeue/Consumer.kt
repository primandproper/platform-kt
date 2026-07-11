package com.primandproper.platform.messagequeue

import com.primandproper.platform.observability.SuspendCloseable
import kotlinx.coroutines.flow.Flow

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
 * Consumes messages from a queue, applying the handler supplied at [ConsumerProvider.consumer]
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

    /**
     * A `Flow`-based alternative to [consume]: a **cold** flow of raw message payloads for this
     * consumer's topic. Each collection subscribes; cancelling or completing the collector releases the
     * underlying subscription (Go's `defer subscription.Close()`). Unlike [consume] — which binds a
     * [ConsumerHandler] at [ConsumerProvider.consumer] time and runs it under the consumer's
     * span/circuit-breaker machinery — [messages] hands the raw stream back so the caller composes
     * delivery with the coroutine operators they prefer (`onEach`, `map`, `buffer`, …).
     */
    public fun messages(): Flow<ByteArray>
}

/**
 * Provides a [Consumer] for a given topic, caching one per topic. Port of platform-go's
 * `messagequeue.ConsumerProvider`.
 */
public interface ConsumerProvider : SuspendCloseable {
    /**
     * Releases the provider's resources — the shared client. `suspend` via [SuspendCloseable] because
     * closing the shared client is transport teardown.
     */
    override suspend fun close()

    /**
     * Returns a [Consumer] for [topic] that drives [handler] over each message, throwing
     * [EmptyTopicNameException] when [topic] is blank. Repeated calls for the same topic return the
     * same cached instance.
     */
    public suspend fun consumer(
        topic: String,
        handler: ConsumerHandler,
    ): Consumer
}
