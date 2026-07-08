package com.primandproper.platform.messagequeue

/**
 * Thrown when a topic name is empty. Port of platform-go's package-level sentinel
 * `messagequeue.ErrEmptyTopicName`, which a `PublisherProvider`/`ConsumerProvider` returns for a
 * blank topic. Callers catch this type rather than comparing sentinel identity — the idiomatic JVM
 * analog of `errors.Is(err, ErrEmptyTopicName)`.
 */
public class EmptyTopicNameException : IllegalArgumentException("empty topic name")

/**
 * Produces messages onto a queue. Port of platform-go's `messagequeue.Publisher`.
 *
 * Go threads a `context.Context` through `Publish`; this port suspends instead. [publishAsync] mirrors
 * Go's fire-and-forget variant, which logs an encountered error rather than surfacing it.
 */
public interface Publisher {
    /**
     * Halts this topic's publishing. Backends that share one underlying client across every topic
     * (Redis) make this a no-op and close the client once via the provider; backends that own a
     * per-topic writer (Kafka) close it here. Mirrors Go's `Stop`.
     */
    public fun stop()

    /** Writes [data] onto the queue, suspending until the send completes, and throwing on failure. */
    public suspend fun publish(data: Any)

    /** Writes [data] onto the queue, logging any error instead of throwing. Mirrors Go's `PublishAsync`. */
    public suspend fun publishAsync(data: Any)
}

/**
 * Provides a [Publisher] for a given topic, caching one per topic. Port of platform-go's
 * `messagequeue.PublisherProvider`.
 */
public interface PublisherProvider {
    /** Releases the provider's resources — the shared client and any cached publishers. */
    public fun close()

    /** Verifies the backend is reachable, throwing if it is not. */
    public suspend fun ping()

    /**
     * Returns a [Publisher] for [topic], throwing [EmptyTopicNameException] when [topic] is blank.
     * Repeated calls for the same topic return the same cached instance.
     */
    public suspend fun providePublisher(topic: String): Publisher
}
