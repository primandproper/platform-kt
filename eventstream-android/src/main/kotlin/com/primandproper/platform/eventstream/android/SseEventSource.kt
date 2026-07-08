package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

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
 */
public class SseEventSource(
    private val client: OkHttpClient,
) {
    /** Opens [request] as an SSE stream and emits each received event. */
    public fun events(request: Request): Flow<Event> =
        callbackFlow {
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        trySend(Event(type ?: "", data))
                    }

                    override fun onClosed(eventSource: EventSource) {
                        close()
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        close(t)
                    }
                }

            val source = EventSources.createFactory(client).newEventSource(request, listener)
            awaitClose { source.cancel() }
        }
}
