package com.primandproper.platform.eventstream

import com.primandproper.platform.observability.SuspendCloseable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * A unidirectional, server-to-client event stream. Port of platform-go's `eventstream.EventStream`.
 *
 * The Go interface is `Send(ctx, *Event) error`, `Done() <-chan struct{}`, `Close() error`. This
 * port keeps the same three operations but expresses them in coroutine terms:
 *
 * - `Send` becomes a `suspend fun` [send] — the coroutine that emits carries the cancellation and
 *   (via the observability `span { }`) the trace context that Go threaded through `ctx`.
 * - `Done() <-chan struct{}` becomes a read-only [done] [Job]: a caller awaits stream termination
 *   with `done.join()`, the coroutine analog of `<-stream.Done()`, and the [Job] completes exactly
 *   when the channel would have closed.
 * - `Close` becomes a `suspend fun` [close] because the real WebSocket teardown suspends.
 *
 * The direction matters: the SERVER (`:eventstream-ktor`) implements this to *emit* over SSE or a
 * WebSocket; the CLIENT (`:eventstream-android`) never implements it — it *consumes* the same events
 * as a `Flow<Event>`, the mirror image of this contract.
 */
public interface EventStream : SuspendCloseable {
    /** Pushes [event] to the client. Throws if the stream is already closed or the write fails. */
    public suspend fun send(event: Event)

    /** Completes when the stream terminates (client disconnect or [close]). Await it with `done.join()`. */
    public val done: Job

    /**
     * Terminates the stream and completes [done]. Idempotent, mirroring Go's `sync.Once`-guarded
     * `Close`. Shares the platform-wide [SuspendCloseable] closer type (P3-12), so a stream can be
     * bracketed with `use { }`.
     */
    override suspend fun close()
}

/**
 * A [EventStream] that additionally receives client-to-server events. Port of platform-go's
 * `eventstream.BidirectionalEventStream`.
 *
 * Go exposes inbound events as `Receive() <-chan *Event`; the idiomatic Kotlin analog is a cold
 * [Flow] of [Event]. Collecting [receive] drains the same inbound frames Go delivered on the channel,
 * and the flow completes when the stream closes — the analog of the channel closing. Only the
 * WebSocket transport is bidirectional; SSE is one-way by construction.
 */
public interface BidirectionalEventStream : EventStream {
    /** A cold [Flow] of inbound events from the client. Completes when the stream closes. */
    public fun receive(): Flow<Event>
}
