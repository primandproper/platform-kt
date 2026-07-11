package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.eventstream.BidirectionalEventStream
import com.primandproper.platform.eventstream.EventStream
import com.primandproper.platform.eventstream.WebSocketConfig
import com.primandproper.platform.observability.Observer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
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
        observer.logger.info("SSE event stream opened on $path")
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
            observer.logger.info("SSE event stream closed on $path")
        }
    }
}

/**
 * Registers a send-only WebSocket endpoint at [path]. [config] enforces the same origin allow-listing
 * as Go's `originChecker` (see [WebSocketConfig.isOriginAllowed]); a disallowed origin is rejected
 * with HTTP 403 during the handshake, before the upgrade completes (see [webSocketWithOriginGuard]).
 */
public fun Route.webSocketEventStream(
    path: String,
    observer: Observer,
    config: WebSocketConfig = WebSocketConfig(),
    handler: suspend (EventStream) -> Unit,
) {
    webSocketWithOriginGuard(path, config) {
        val stream = WebSocketEventStream(this, observer)
        observer.logger.info("WebSocket event stream opened on $path")
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
            observer.logger.info("WebSocket event stream closed on $path")
        }
    }
}

/**
 * Registers a bidirectional WebSocket endpoint at [path], handing a [BidirectionalWebSocketEventStream]
 * to [handler] so it can both emit events and collect [BidirectionalEventStream.receive]. Origin is
 * enforced the same way as [webSocketEventStream] — rejected with HTTP 403 before the upgrade.
 */
public fun Route.bidirectionalWebSocketEventStream(
    path: String,
    observer: Observer,
    config: WebSocketConfig = WebSocketConfig(),
    handler: suspend (BidirectionalEventStream) -> Unit,
) {
    webSocketWithOriginGuard(path, config) {
        val stream = BidirectionalWebSocketEventStream(this, observer)
        observer.logger.info("bidirectional WebSocket event stream opened on $path")
        try {
            handler(stream)
            stream.done.join()
        } finally {
            stream.close()
            observer.logger.info("bidirectional WebSocket event stream closed on $path")
        }
    }
}

/**
 * Registers a WebSocket route at [path] that validates the request `Origin` against [config] *before*
 * the WebSocket upgrade. The origin check is a route-scoped plugin whose `onCall` hook fires ahead of
 * the route handler that performs the HTTP 101 upgrade, so a disallowed origin is answered with HTTP
 * 403 and the connection never upgrades — [body] never runs. This is the fix for Cross-Site WebSocket
 * Hijacking: running the check inside the `webSocket {}` body would only close the socket *after* the
 * 101 upgrade already succeeded.
 */
private fun Route.webSocketWithOriginGuard(
    path: String,
    config: WebSocketConfig,
    body: suspend DefaultWebSocketServerSession.() -> Unit,
) {
    route(path) {
        install(originGuardPlugin(config))
        webSocket { body() }
    }
}

/**
 * A route-scoped plugin that answers a disallowed WebSocket handshake with HTTP 403 *before* the
 * upgrade. Ktor's `webSocket {}` installs its handler on a child route gated by an `Upgrade: websocket`
 * header selector; a route-scoped plugin installed on the enclosing route propagates to that child and
 * its `onCall` hook runs ahead of the upgrade handler. On a disallowed origin it responds 403, which
 * commits the response, so the subsequent upgrade handler cannot complete the 101 handshake. A raw
 * route interceptor cannot be used here — `webSocket {}`'s child route is not reached by parent-route
 * interceptors, whereas route-scoped plugins are. Checking origin inside the `webSocket {}` body would
 * only close the socket *after* the 101 upgrade already succeeded (the CSWSH hole).
 */
private fun originGuardPlugin(config: WebSocketConfig) =
    createRouteScopedPlugin("EventStreamOriginGuard") {
        onCall { call ->
            val origin = call.request.headers[HttpHeaders.Origin]
            val host = call.request.headers[HttpHeaders.Host]
            if (!config.isOriginAllowed(origin, host)) {
                call.application.log.warn(
                    "rejected WebSocket handshake: origin '{}' not allowed for host '{}'",
                    origin,
                    host,
                )
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
