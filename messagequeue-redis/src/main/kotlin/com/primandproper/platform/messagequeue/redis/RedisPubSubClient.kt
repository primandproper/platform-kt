package com.primandproper.platform.messagequeue.redis

import kotlinx.coroutines.flow.Flow

/**
 * The minimal Redis pub/sub surface the Redis publisher and consumer need. Port of the small
 * interfaces platform-go's `messagequeue/redis` defines over go-redis (`messagePublisher`,
 * `subscriptionProvider`, `channelProvider`) — they exist for the same reason: so the backend can be
 * unit-tested against a fake without a live server. [LettuceRedisPubSubClient] is the production
 * adapter.
 *
 * All command methods suspend: the Lettuce adapter bridges each `RedisFuture` (a `CompletionStage`) to
 * a coroutine via `kotlinx.coroutines.future.await`.
 */
public interface RedisPubSubClient {
    /**
     * Publishes [message] to [channel], returning the number of clients that received it — the Redis
     * `PUBLISH` reply, the analog of go-redis's `Publish(...).Result()`.
     */
    public suspend fun publish(
        channel: String,
        message: ByteArray,
    ): Long

    /** Verifies the server is reachable (Redis `PING`). */
    public suspend fun ping()

    /**
     * Subscribes to [channel] and returns a live [Subscription] whose [Subscription.messages] flow
     * emits each published payload. Mirrors go-redis's `Subscribe` returning a `*PubSub`.
     */
    public suspend fun subscribe(channel: String): Subscription

    /** Closes the underlying client and its connections. */
    public fun close()
}

/**
 * A live subscription to one channel. Port of go-redis's `channelProvider` (`Channel()` + `Close()`):
 * [messages] is the analog of the `<-chan *redis.Message`, and [close] unsubscribes and releases the
 * server-side subscription rather than leaking it.
 */
public interface Subscription {
    /** A cold flow of raw message payloads delivered on the subscribed channel. */
    public fun messages(): Flow<ByteArray>

    /** Unsubscribes and releases the subscription. */
    public suspend fun close()
}
