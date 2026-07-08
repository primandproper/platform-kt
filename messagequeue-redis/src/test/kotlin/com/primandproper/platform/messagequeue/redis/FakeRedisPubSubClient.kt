package com.primandproper.platform.messagequeue.redis

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * An in-memory [RedisPubSubClient] fake standing in for a live server so the Redis publisher/consumer
 * mapping can be unit-tested — the analog of the go-redis mock in platform-go's tests. Records every
 * publish and hands each subscription a [Channel] the test feeds messages into.
 */
class FakeRedisPubSubClient(
    var failPublish: Boolean = false,
) : RedisPubSubClient {
    val published: MutableList<Pair<String, ByteArray>> = mutableListOf()
    val subscriptions: MutableList<FakeSubscription> = mutableListOf()
    var pingCount: Int = 0
    var closed: Boolean = false

    override suspend fun publish(
        channel: String,
        message: ByteArray,
    ): Long {
        if (failPublish) throw RuntimeException("injected publish failure")
        published += channel to message
        return 1
    }

    override suspend fun ping() {
        pingCount++
    }

    override suspend fun subscribe(channel: String): Subscription {
        val sub = FakeSubscription(channel)
        subscriptions += sub
        return sub
    }

    override fun close() {
        closed = true
    }
}

/** A [Subscription] the test emits into via [emit]; collection ends when [close] closes the channel. */
class FakeSubscription(
    val channel: String,
) : Subscription {
    private val buffer: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)
    var closed: Boolean = false

    suspend fun emit(message: ByteArray) {
        buffer.send(message)
    }

    override fun messages(): Flow<ByteArray> = buffer.receiveAsFlow()

    override suspend fun close() {
        closed = true
        buffer.close()
    }
}
