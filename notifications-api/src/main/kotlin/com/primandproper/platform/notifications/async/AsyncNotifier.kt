package com.primandproper.platform.notifications.async

import com.primandproper.platform.observability.SuspendCloseable

/**
 * An async notification event published to a named channel. Port of platform-go's `async.Event`.
 *
 * [type] is the event discriminator a subscriber routes on; [data] is the opaque JSON payload (Go's
 * `json.RawMessage`, carried here as a raw string so this abstraction stays serialization-free — a
 * backend or caller decides how to encode it). A `null` [data] mirrors Go's `omitempty` absent field.
 */
public data class AsyncEvent(
    val type: String,
    val data: String? = null,
)

/**
 * Publishes events to named channels — the port of platform-go's `async.AsyncNotifier`.
 *
 * Implementations may deliver via WebSocket, SSE, Pusher, or Ably (Go's backends); this port ships an
 * in-memory [InMemoryAsyncNotifier] whose delivery ordering and retry are unit-testable without a
 * transport, and leaves the networked backends as documented seams. Go's `Publish`/`Close` thread a
 * `context.Context` and return an `error`; this port suspends instead and throws on failure.
 */
public interface AsyncNotifier : SuspendCloseable {
    /** Sends [event] to every subscriber of [channel], throwing on failure. */
    public suspend fun publish(
        channel: String,
        event: AsyncEvent,
    )

    /**
     * Releases resources held by the notifier. Idempotent. `suspend` via [SuspendCloseable] because
     * the networked backends (WebSocket/SSE/Pusher/Ably) tear down a real transport.
     */
    override suspend fun close()
}
