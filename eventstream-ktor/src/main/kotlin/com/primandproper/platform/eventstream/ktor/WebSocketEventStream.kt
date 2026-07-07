package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.errors.newError
import com.primandproper.platform.eventstream.BidirectionalEventStream
import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventCodec
import com.primandproper.platform.eventstream.EventStream
import com.primandproper.platform.eventstream.decodeEvents
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.span
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A send-only WebSocket [EventStream] over a Ktor [DefaultWebSocketServerSession]. Port of
 * platform-go's `eventstream/websocket.wsStream`, the SERVER emit side.
 *
 * Each [send] opens a `ws_send` [Observer] span (Go's `BeginCustom(ctx, "ws_send")`) and writes the
 * event as the JSON envelope [EventCodec] produces — the analog of Go's `conn.WriteJSON(event)`.
 *
 * The heartbeat is a documented seam: Go runs a manual `heartbeatLoop` pinging the connection and a
 * `readPump` draining control frames. Ktor's WebSockets plugin owns both — configure `pingPeriod`
 * when installing the plugin (see [installEventStreamWebSockets], wired from
 * `WebSocketConfig.heartbeatInterval`) and it pings idle connections and processes pong/close frames
 * for us, so no hand-rolled loop is needed here.
 */
public open class WebSocketEventStream internal constructor(
    private val session: DefaultWebSocketServerSession,
    private val o11y: Observer,
) : EventStream {
    private val _done = CompletableDeferred<Unit>()

    override val done: Job get() = _done

    override suspend fun send(event: Event) {
        o11y.span("ws_send") {
            set("event.type", event.type)

            if (_done.isCompleted) throw error(newError("stream closed"), "sending to a closed stream")

            session.send(Frame.Text(EventCodec.encode(event)))
        }
    }

    override suspend fun close() {
        if (_done.complete(Unit)) {
            runCatching { session.close() }
        }
    }
}

/**
 * A bidirectional WebSocket stream: [WebSocketEventStream] plus a [receive] [Flow] of inbound events.
 * Port of platform-go's `eventstream/websocket.bidirectionalWSStream`.
 *
 * Go's `readLoop` reads frames, `json.Unmarshal`s each, and `continue`s past a malformed one, pushing
 * the rest onto a buffered channel. The [Flow] analog collects the session's incoming text frames and
 * decodes each via [decodeEvents], which drops any frame that fails to parse — the same skip-and-keep
 * behavior. The flow completes when the session's incoming channel closes (the client disconnects).
 */
public class BidirectionalWebSocketEventStream internal constructor(
    private val session: DefaultWebSocketServerSession,
    o11y: Observer,
) : WebSocketEventStream(session, o11y),
    BidirectionalEventStream {
    override fun receive(): Flow<Event> =
        session.incoming
            .receiveAsFlow()
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .decodeEvents()
}
