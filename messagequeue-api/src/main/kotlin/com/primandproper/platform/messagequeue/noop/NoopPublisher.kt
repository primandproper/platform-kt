package com.primandproper.platform.messagequeue.noop

import com.primandproper.platform.messagequeue.Publisher
import com.primandproper.platform.messagequeue.PublisherProvider

/**
 * A no-op [Publisher]: every publish is discarded. Port of platform-go's `messagequeue/noop.publisher`
 * — a safe default for wiring, and for tests that don't care about real delivery.
 */
public class NoopPublisher<T : Any> : Publisher<T> {
    override suspend fun close() {}

    override suspend fun publish(data: T) {}

    override suspend fun publishAsync(data: T) {}
}

/**
 * A no-op [PublisherProvider] handing out [NoopPublisher]s. Port of platform-go's
 * `messagequeue/noop.publisherProvider`.
 *
 * Go's noop `ProvidePublisher` never inspects the topic, so this port keeps that behavior — a blank
 * topic is accepted here, leaving [com.primandproper.platform.messagequeue.EmptyTopicNameException] to
 * the real backends that cache per topic.
 */
public class NoopPublisherProvider<T : Any> : PublisherProvider<T> {
    override suspend fun close() {}

    override suspend fun ping() {}

    override suspend fun publisher(topic: String): Publisher<T> = NoopPublisher()
}
