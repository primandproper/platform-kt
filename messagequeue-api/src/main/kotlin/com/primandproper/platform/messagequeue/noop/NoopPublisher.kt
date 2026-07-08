package com.primandproper.platform.messagequeue.noop

import com.primandproper.platform.messagequeue.Publisher
import com.primandproper.platform.messagequeue.PublisherProvider

/**
 * A no-op [Publisher]: every publish is discarded. Port of platform-go's `messagequeue/noop.publisher`
 * — a safe default for wiring, and for tests that don't care about real delivery.
 */
public class NoopPublisher : Publisher {
    override fun stop() {}

    override suspend fun publish(data: Any) {}

    override suspend fun publishAsync(data: Any) {}
}

/**
 * A no-op [PublisherProvider] handing out [NoopPublisher]s. Port of platform-go's
 * `messagequeue/noop.publisherProvider`.
 *
 * Go's noop `ProvidePublisher` never inspects the topic, so this port keeps that behavior — a blank
 * topic is accepted here, leaving [com.primandproper.platform.messagequeue.EmptyTopicNameException] to
 * the real backends that cache per topic.
 */
public class NoopPublisherProvider : PublisherProvider {
    override fun close() {}

    override suspend fun ping() {}

    override suspend fun providePublisher(topic: String): Publisher = NoopPublisher()
}
