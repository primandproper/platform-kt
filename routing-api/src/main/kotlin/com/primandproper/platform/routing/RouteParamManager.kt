package com.primandproper.platform.routing

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.ensureLogger

/**
 * Builds route-param fetchers for a router. Direct port of Go's `routing.RouteParamManager`.
 *
 * In Go this interface is implemented only in `routing/chi`, because its fetchers call
 * `chi.URLParam`. Here path-parameter access is abstracted behind [RoutingRequest.pathParameter], so
 * the fetchers are framework-independent and the sole implementation, [DefaultRouteParamManager],
 * lives in this API module — the provider (`:routing-ktor`) only supplies the [RoutingRequest] that
 * reads a Ktor call's parameters. Documented divergence, noted where it matters.
 */
public interface RouteParamManager {
    /**
     * Builds a fetcher that reads the [key] path parameter and parses it as an unsigned 64-bit ID.
     * On a missing or non-numeric value it returns `0uL` and logs the failure (using [logDescription],
     * or [key] when that is blank) — the `uint64`-only signature cannot distinguish a parse failure
     * from a real ID of 0, so, like Go, it always logs rather than swallowing the error.
     */
    public fun buildRouteParamIDFetcher(
        logger: Logger?,
        key: String,
        logDescription: String,
    ): (RoutingRequest) -> ULong

    /** Builds a fetcher that returns the [key] path parameter as a string (empty if absent). */
    public fun buildRouteParamStringIDFetcher(key: String): (RoutingRequest) -> String
}

/**
 * The shared fetcher behind both [DefaultRouteParamManager] and the router's own route-param methods.
 * Port of Go's package-level `buildRouteParamIDFetcher`, including its "always log the parse failure"
 * behavior.
 */
public fun buildRouteParamIDFetcher(
    logger: Logger?,
    key: String,
    logDescription: String,
): (RoutingRequest) -> ULong {
    val log = ensureLogger(logger)
    return { req ->
        val raw = req.pathParameter(key) ?: ""
        val parsed = raw.toULongOrNull()
        if (parsed == null) {
            val desc = logDescription.ifEmpty { key }
            log.error(
                "fetching $desc ID from request (value \"$raw\")",
                NumberFormatException("not an unsigned 64-bit integer: \"$raw\""),
            )
        }
        parsed ?: 0uL
    }
}

/**
 * The framework-independent [RouteParamManager]. Analog of Go's `chi.chiRouteParamManager`, but free
 * of any provider dependency because it reads parameters through [RoutingRequest] rather than
 * `chi.URLParam`.
 */
public object DefaultRouteParamManager : RouteParamManager {
    override fun buildRouteParamIDFetcher(
        logger: Logger?,
        key: String,
        logDescription: String,
    ): (RoutingRequest) -> ULong =
        // Fully qualified: an unqualified call would resolve to this override itself (the member
        // shadows the same-named package-level function), recursing infinitely.
        com.primandproper.platform.routing.buildRouteParamIDFetcher(logger, key, logDescription)

    override fun buildRouteParamStringIDFetcher(key: String): (RoutingRequest) -> String = { req -> req.pathParameter(key) ?: "" }
}
