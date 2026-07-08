package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

private const val NORMAL_CLOSURE = 1000

/**
 * A live, bidirectional WebSocket session against a server endpoint. The CLIENT mirror of the
 * server's `:eventstream-ktor` WebSocket stream: [incoming] is the `Flow<Event>` the app reads
 * (Go's server never consumes — this is the flipped direction), and [send] pushes an event back the
 * other way, both using the shared [EventCodec] envelope.
 *
 * Inbound frames that fail to decode are dropped, mirroring the server read loop's skip-and-continue.
 * [incoming] completes when the socket closes and fails on a transport error.
 */
public class WebSocketEventSession internal constructor(
    private val webSocket: WebSocket,
    private val events: Channel<Event>,
) {
    /** A cold [Flow] of inbound events. Completes when the socket closes. */
    public val incoming: Flow<Event> = events.receiveAsFlow()

    /** Sends [event] to the server as the JSON envelope. Returns false if the socket is already closing. */
    public fun send(event: Event): Boolean = webSocket.send(EventCodec.encode(event))

    /** Initiates a graceful close of the socket. */
    public fun close() {
        webSocket.close(NORMAL_CLOSURE, null)
    }
}

/**
 * Opens WebSocket event sessions against a server. The CONSUME (and reply) counterpart to the
 * server-emit `:eventstream-ktor` WebSocket transport.
 */
public class WebSocketEventClient(
    private val client: OkHttpClient,
) {
    /**
     * Opens [request] as a WebSocket and returns the live [WebSocketEventSession]. The session's
     * [WebSocketEventSession.incoming] flow is fed by the socket's frames as they arrive.
     */
    public fun open(request: Request): WebSocketEventSession {
        val channel = Channel<Event>(Channel.BUFFERED)
        val listener =
            object : WebSocketListener() {
                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    val event = runCatching { EventCodec.decode(text) }.getOrNull()
                    if (event != null) channel.trySend(event)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString,
                ) {
                    onMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    channel.close()
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    channel.close()
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    channel.close(t)
                }
            }

        val webSocket = client.newWebSocket(request, listener)
        return WebSocketEventSession(webSocket, channel)
    }

    /** Convenience: open [request] and expose only its inbound [Event] flow. */
    public fun events(request: Request): Flow<Event> = open(request).incoming
}
