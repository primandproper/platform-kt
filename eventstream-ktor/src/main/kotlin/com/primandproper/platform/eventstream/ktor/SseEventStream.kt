package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.errors.newError
import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventStream
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.span
import io.ktor.server.sse.ServerSSESession
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/**
 * A server-to-client SSE [EventStream] over a Ktor [ServerSSESession]. Port of platform-go's
 * `eventstream/sse.sseStream`, the SERVER emit side.
 *
 * Each [send] opens an `sse_send` [Observer] span (Go's `BeginCustom(ctx, "sse_send")`) recording the
 * event type and payload length, then writes one [ServerSentEvent]. Ktor serializes the payload into
 * one `data:` line per source line, which — together with stripping CR/LF from the `event:` field —
 * gives the same SSE-injection defense Go builds by hand in `sseStream.Send` (a newline-bearing type
 * or payload cannot inject extra SSE fields).
 *
 * Go signals termination by closing a `Done()` channel; here [done] is a [Job] completed by [close]
 * (or when the Ktor session's coroutine is cancelled on client disconnect). SSE is one-way, so there
 * is no bidirectional counterpart — matching Go, where `sse` implements only `EventStreamUpgrader`.
 */
public class SseEventStream internal constructor(
    private val session: ServerSSESession,
    private val o11y: Observer,
) : EventStream {
    private val _done = CompletableDeferred<Unit>()

    override val done: Job get() = _done

    override suspend fun send(event: Event) {
        o11y.span("sse_send") {
            set("event.type", event.type)
            set(Keys.LENGTH, event.payload?.length ?: 0)

            if (_done.isCompleted) throw error(newError("stream closed"), "sending to a closed stream")

            // Event types are single-line tokens; strip CR/LF so a newline-bearing type can't inject
            // additional SSE fields. Ktor splits the payload across `data:` lines for the same reason.
            val sanitizedType = event.type.replace("\r", "").replace("\n", "").ifEmpty { null }
            session.send(ServerSentEvent(data = event.payload, event = sanitizedType))
        }
    }

    override suspend fun close() {
        _done.complete(Unit)
    }
}
