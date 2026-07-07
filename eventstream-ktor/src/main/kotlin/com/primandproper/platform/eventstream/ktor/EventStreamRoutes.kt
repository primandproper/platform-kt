package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.eventstream.BidirectionalEventStream
import com.primandproper.platform.eventstream.EventStream
import com.primandproper.platform.eventstream.WebSocketConfig
import com.primandproper.platform.observability.Observer
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlin.time.Duration

/*
 * Route wiring for the SERVER emit side. These are the Ktor analog of Go's
 * `ProvideEventStreamUpgrader` switch: Go picks an `EventStreamUpgrader` and calls it per request,
 * whereas Ktor upgrades through route builders, so the port registers an endpoint that constructs the
 * transport stream, hands it to a handler, and keeps the connection open until the stream closes.
 */

/** Installs Ktor's SSE plugin. Call once from `Application` before registering [sseEventStream] routes. */
public fun Application.installEventStreamSse() {
    install(SSE)
}

/**
 * Installs Ktor's WebSockets plugin, mapping [WebSocketConfig.heartbeatInterval] onto Ktor's
 * `pingPeriod` — the replacement for Go's manual `heartbeatLoop`. A zero interval disables pinging.
 * Call once from `Application` before registering WebSocket routes.
 */
public fun Application.installEventStreamWebSockets(config: WebSocketConfig = WebSocketConfig()) {
    install(WebSockets) {
        if (config.heartbeatInterval > Duration.ZERO) {
            // Ktor 3.0 exposes the ping interval as a plain millis member on WebSocketOptions
            // (the kotlin.time.Duration `pingPeriod` form is an extension requiring a separate import).
            pingPeriodMillis = config.heartbeatInterval.inWholeMilliseconds
        }
    }
}

/**
 * Registers an SSE endpoint at [path] that hands a unidirectional [SseEventStream] to [handler]
 * (typically to register it with a `StreamManager`). The connection stays open until the stream is
 * closed or the client disconnects; [observer] spans each send.
 */
public fun Route.sseEventStream(
    path: String,
    observer: Observer,
    handler: suspend (EventStream) -> Unit,
) {
    sse(path) {
        val stream = SseEventStream(this, observer)
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
        }
    }
}

/**
 * Registers a send-only WebSocket endpoint at [path]. [config] enforces the same origin allow-listing
 * as Go's `originChecker` (see [WebSocketConfig.isOriginAllowed]); a disallowed origin is rejected
 * before the handler runs.
 */
public fun Route.webSocketEventStream(
    path: String,
    observer: Observer,
    config: WebSocketConfig = WebSocketConfig(),
    handler: suspend (EventStream) -> Unit,
) {
    webSocket(path) {
        if (!config.isOriginAllowed(call.request.headers[HttpHeaders.Origin])) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin not allowed"))
            return@webSocket
        }
        val stream = WebSocketEventStream(this, observer)
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
        }
    }
}

/**
 * Registers a bidirectional WebSocket endpoint at [path], handing a [BidirectionalWebSocketEventStream]
 * to [handler] so it can both emit events and collect [BidirectionalEventStream.receive].
 */
public fun Route.bidirectionalWebSocketEventStream(
    path: String,
    observer: Observer,
    config: WebSocketConfig = WebSocketConfig(),
    handler: suspend (BidirectionalEventStream) -> Unit,
) {
    webSocket(path) {
        if (!config.isOriginAllowed(call.request.headers[HttpHeaders.Origin])) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin not allowed"))
            return@webSocket
        }
        val stream = BidirectionalWebSocketEventStream(this, observer)
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
        }
    }
}
