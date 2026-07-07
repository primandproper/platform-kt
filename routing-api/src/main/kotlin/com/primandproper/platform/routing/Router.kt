package com.primandproper.platform.routing

/**
 * A request handler. Port of Go's `http.Handler` (`ServeHTTP(w, r)`), collapsed with `http.HandlerFunc`
 * into one `fun interface`: Kotlin has a single functional handler type, so the router's `Handle` /
 * `HandleFunc` pair (which in Go exists only to bridge the interface and the func adapter) both take
 * this. Suspend, because a Kotlin handler runs on the calling coroutine rather than a blocked goroutine.
 */
public fun interface HttpHandler {
    public suspend fun handle(call: RoutingCall)
}

/**
 * A middleware handler: wraps a [HttpHandler] to produce a new one. Direct port of Go's
 * `routing.Middleware` (`func(http.Handler) http.Handler`). Composition order matches Go/chi: in
 * `withMiddleware(a, b)` / `addRoute(..., a, b)`, `a` is outermost and runs first.
 */
public typealias Middleware = (HttpHandler) -> HttpHandler

/**
 * A registered HTTP route with its method and path. Direct port of Go's `routing.Route`.
 */
public data class Route(
    public val method: String,
    public val path: String,
)

/**
 * The provider-neutral handle returned by [Router.handler], the analog of the `http.Handler` Go's
 * `routing.Router.Handler()` returns.
 *
 * In Go the router and the server both speak `net/http`, so `Handler()` can hand the server a
 * `http.Handler` the server mounts directly. Ktor has no such universal handler type — its routing is
 * declared onto an `Application` — so there is nothing concrete a framework-free API can name here.
 * This marker preserves exactly that boundary: [Router.handler] returns an opaque [MountableHandler],
 * and a *matching* server backend (`:server-ktor` for a `:routing-ktor` router) knows how to mount it.
 * This is the one seam where "Ktor merges router and server concerns Go keeps split" surfaces, and it
 * is kept explicit rather than papered over.
 */
public interface MountableHandler

/**
 * The contract between the routing library and its caller. Direct port of Go's `routing.Router`,
 * itself modeled on go-chi/chi.
 *
 * Divergences from Go, all forced by the language rather than by design:
 * - `Handler()` returns a [MountableHandler] instead of `http.Handler` (see that type's docs).
 * - `Handle` and `HandleFunc` both take [HttpHandler] (Kotlin has no `Handler`/`HandlerFunc` split);
 *   [handleFunc] is retained for parity and simply delegates to [handle].
 * - `AddRoute` returns Go's `error`; here [addRoute] throws [IllegalArgumentException] for an unknown
 *   method, the idiomatic Kotlin equivalent of Go's `errInvalidMethod`.
 * - Metrics (Go wires an otelchi metrics provider) are a documented `TODO(metrics)` seam in the
 *   provider, since observability-api has no metrics pillar yet — the same stance `:cache-api` takes.
 */
public interface Router {
    /** Returns every registered route, mirroring Go's `Routes()` (which walks the chi tree). */
    public fun routes(): List<Route>

    /** Returns the mountable handle a matching server backend installs. Analog of Go's `Handler()`. */
    public fun handler(): MountableHandler

    /** Registers [handler] at [pattern] for every HTTP method. Analog of Go's `Handle`. */
    public fun handle(
        pattern: String,
        handler: HttpHandler,
    )

    /** Alias of [handle], retained for parity with Go's `HandleFunc`. */
    public fun handleFunc(
        pattern: String,
        handler: HttpHandler,
    )

    /** Returns a router that applies [middleware] to every route subsequently registered through it. */
    public fun withMiddleware(vararg middleware: Middleware): Router

    /** Applies a set of routes to a subrouter mounted at [pattern]. Analog of Go's `Route`. */
    public fun route(
        pattern: String,
        fn: (Router) -> Unit,
    ): Router

    public fun connect(
        pattern: String,
        handler: HttpHandler,
    )

    public fun delete(
        pattern: String,
        handler: HttpHandler,
    )

    public fun get(
        pattern: String,
        handler: HttpHandler,
    )

    public fun head(
        pattern: String,
        handler: HttpHandler,
    )

    public fun options(
        pattern: String,
        handler: HttpHandler,
    )

    public fun patch(
        pattern: String,
        handler: HttpHandler,
    )

    public fun post(
        pattern: String,
        handler: HttpHandler,
    )

    public fun put(
        pattern: String,
        handler: HttpHandler,
    )

    public fun trace(
        pattern: String,
        handler: HttpHandler,
    )

    /**
     * Registers [handler] for [method] at [path], wrapped in [middleware]. Throws
     * [IllegalArgumentException] if [method] is not a known HTTP verb, mirroring Go's `errInvalidMethod`.
     */
    public fun addRoute(
        method: String,
        path: String,
        handler: HttpHandler,
        vararg middleware: Middleware,
    )
}
