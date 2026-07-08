package com.primandproper.platform.routing

/**
 * The read-only view of an in-flight request a handler or route-param fetcher inspects. Port of the
 * parts of Go's `*http.Request` the routing layer actually touches: the method, the path, the
 * router-extracted path parameters, and request headers.
 *
 * platform-go passes the concrete `*http.Request` everywhere; a Kotlin port over Ktor cannot leak
 * Ktor's `ApplicationCall` into this framework-free API module, so the request surface the routing
 * abstraction depends on is captured here and adapted by each provider (`:routing-ktor` supplies the
 * Ktor-backed implementation). This is also what lets [RouteParamManager]'s fetchers — which in Go
 * are welded to `chi.URLParam` — live in this module instead of the provider: path-parameter access
 * is abstracted behind [pathParameter], so the numeric-parsing logic is framework-independent.
 */
public interface RoutingRequest {
    /** The request's HTTP method (uppercased verb, e.g. `"GET"`). */
    public val method: String

    /** The request path, without query string (e.g. `"/things/123"`). */
    public val path: String

    /** The value of the router-bound path parameter named [key], or `null` if absent. Analog of `chi.URLParam`. */
    public fun pathParameter(key: String): String?

    /** The first value of request header [name] (case-insensitive), or `null` if absent. */
    public fun header(name: String): String?
}

/**
 * The full per-request handle a [HttpHandler] receives: everything in [RoutingRequest] plus the means
 * to write a response. Collapses Go's `(http.ResponseWriter, *http.Request)` handler pair into one
 * suspend-friendly object, the shape Ktor's `ApplicationCall` already takes. Response writers are
 * `suspend`, matching Ktor's natively-suspending response API.
 */
public interface RoutingCall : RoutingRequest {
    /** The request ID assigned to this call by the provider's request-ID middleware, or `null`. */
    public val requestId: String?

    /** Writes [text] with the given [status] code and a `text/plain` content type. */
    public suspend fun respondText(
        status: Int,
        text: String,
    )

    /** Writes [bytes] with the given [status] and [contentType]. */
    public suspend fun respondBytes(
        status: Int,
        contentType: String,
        bytes: ByteArray,
    )

    /** Writes an empty response carrying only [status]. Analog of `http.ResponseWriter.WriteHeader`. */
    public suspend fun respondStatus(status: Int)
}
