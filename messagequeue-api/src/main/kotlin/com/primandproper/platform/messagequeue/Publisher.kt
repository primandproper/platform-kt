package com.primandproper.platform.messagequeue

import com.primandproper.platform.observability.SuspendCloseable

/**
 * Thrown when a topic name is empty. Port of platform-go's package-level sentinel
 * `messagequeue.ErrEmptyTopicName`, which a `PublisherProvider`/`ConsumerProvider` returns for a
 * blank topic. Callers catch this type rather than comparing sentinel identity — the idiomatic JVM
 * analog of `errors.Is(err, ErrEmptyTopicName)`.
 */
public class EmptyTopicNameException : IllegalArgumentException("empty topic name")

/**
 * Produces messages of type [T] onto a queue. Port of platform-go's `messagequeue.Publisher`.
 *
 * Go threads a `context.Context` through `Publish`; this port suspends instead. [publishAsync] mirrors
 * Go's fire-and-forget variant, which logs an encountered error rather than surfacing it. The type
 * parameter, paired with a [MessageEncoder] of the same [T], replaces Go's reflective `Publish(any)`
 * with a compile-time-checked payload — publishing the wrong type no longer fails at runtime.
 */
public interface Publisher<T : Any> : SuspendCloseable {
    /**
     * Halts this topic's publishing. Backends that share one underlying client across every topic
     * (Redis) make this a no-op and close the client once via the provider; backends that own a
     * per-topic writer (Kafka) close it here. Renamed from Go's `Stop` to `close` for consistency with
     * the platform-wide [SuspendCloseable] convention (P3-12); `suspend` so a per-topic writer can
     * flush without blocking.
     */
    override suspend fun close()

    /** Writes [data] onto the queue, suspending until the send completes, and throwing on failure. */
    public suspend fun publish(data: T)

    /** Writes [data] onto the queue, logging any error instead of throwing. Mirrors Go's `PublishAsync`. */
    public suspend fun publishAsync(data: T)
}

/**
 * Provides a [Publisher] of type [T] for a given topic, caching one per topic. Port of platform-go's
 * `messagequeue.PublisherProvider`. The message type is fixed at provider construction (that is where
 * the [MessageEncoder] is injected), so every publisher a provider hands out shares the same [T].
 */
public interface PublisherProvider<T : Any> : SuspendCloseable {
    /**
     * Releases the provider's resources — the shared client and any cached publishers. `suspend` via
     * [SuspendCloseable] because closing the shared client is transport teardown.
     */
    override suspend fun close()

    /** Verifies the backend is reachable, throwing if it is not. */
    public suspend fun ping()

    /**
     * Returns a [Publisher] for [topic], throwing [EmptyTopicNameException] when [topic] is blank.
     * Repeated calls for the same topic return the same cached instance.
     */
    public suspend fun publisher(topic: String): Publisher<T>
}
