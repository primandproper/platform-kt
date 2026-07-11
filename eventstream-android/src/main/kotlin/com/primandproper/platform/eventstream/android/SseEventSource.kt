package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

/**
 * An SSE event paired with its server-assigned `id:` field — i.e. the value a client echoes back as
 * `Last-Event-ID` to resume a dropped stream. [id] is `null` when the server sent no id for the event.
 */
public data class SseMessage(
    val event: Event,
    val id: String?,
)

/**
 * Consumes a server SSE endpoint as a cold [Flow] of [Event]. The CLIENT mirror of the server's
 * `:eventstream-ktor` SSE emitter — the direction flip that this Android package exists for: Go only
 * ever *emits* SSE, while here the app *reads* it.
 *
 * OkHttp's `okhttp-sse` does the SSE framing (reassembling multi-line `data:` blocks, dispatching one
 * callback per event); each callback becomes an [Event] whose [Event.type] is the SSE `event:` field
 * and whose [Event.payload] is the reassembled `data` — the exact inverse of what the server wrote in
 * `sseStream.Send`. The flow completes when the server closes the stream and fails (propagating the
 * cause) on a transport error; cancelling the collector cancels the underlying [EventSource.cancel].
 *
 * An optional [observer] is notified when an event is dropped under backpressure (a burst larger than
 * the flow's buffer would otherwise vanish silently); it defaults to [NoopEventStreamObserver].
 */
public class SseEventSource(
    private val client: OkHttpClient,
    private val observer: EventStreamObserver = NoopEventStreamObserver,
) {
    /** Opens [request] as an SSE stream and emits each received event. */
    public fun events(request: Request): Flow<Event> = messages(request).map { it.event }

    /**
     * Like [events], but each emission carries the SSE `id:` ([SseMessage.id]) alongside the event, so
     * a caller can persist the last id and resume after a disconnect by re-issuing [request] with a
     * `Last-Event-ID` request header set to it.
     *
     * TODO(reconnect): add a reconnecting variant that wraps this in `:retry`'s `Flow.retryWithPolicy`
     *  and, on each re-collection, re-issues [request] with the last observed [SseMessage.id] as the
     *  `Last-Event-ID` header so the server replays from where the stream dropped. It is left out of
     *  this change to avoid pulling `:retry` into the Android transport module; the id is captured and
     *  exposed here so the wrapper is a pure, additive layer on top of [messages].
     */
    public fun messages(request: Request): Flow<SseMessage> =
        callbackFlow {
            var dropped = 0L
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        val message = SseMessage(Event(type ?: "", data), id)
                        if (trySend(message).isFailure) {
                            dropped++
                            observer.onDroppedEvent(message.event, dropped)
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        close()
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        // okhttp-sse reports a non-2xx HTTP response (or a non-SSE content type) by
                        // calling onFailure with a *null* throwable. Closing the flow normally there
                        // would make a failed connect look like an empty, successfully-completed stream;
                        // synthesize a cause so the collector sees the failure instead.
                        close(t ?: sseFailure(response))
                    }
                }

            val source = EventSources.createFactory(client).newEventSource(request, listener)
            awaitClose { source.cancel() }
        }

    private fun sseFailure(response: Response?): Throwable {
        val code = response?.code
        return IOException(
            if (code != null) {
                "SSE stream failed: HTTP $code"
            } else {
                "SSE stream failed before receiving a response"
            },
        )
    }
}
