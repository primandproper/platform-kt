package com.primandproper.platform.server

import com.primandproper.platform.routing.Router

/**
 * An HTTP server composed of multiple HTTP services. Port of platform-go's `http.Server` interface
 * (in `server/http`).
 *
 * Divergences from Go, forced by the runtime:
 * - `Serve()` in Go blocks until the listener closes (`ListenAndServe`). Here [serve] is `suspend`
 *   and *starts* the engine, returning once it is bound — a background server is the idiomatic
 *   coroutine shape, and it keeps the type testable without owning the calling thread. Callers that
 *   want to block can `join` the engine themselves.
 * - `Shutdown(ctx) error` becomes [shutdown] taking a grace period; Go's context deadline maps onto
 *   Ktor's grace/timeout millis. Trace flushing on shutdown (Go's `tracerProvider.ForceFlush`) is a
 *   `TODO(trace-flush)` seam until observability-api exposes a flush hook.
 */
public interface Server {
    /** Starts serving HTTP traffic. Returns once the engine is bound (see the type doc on the Go divergence). */
    public suspend fun serve()

    /**
     * Stops the server, draining in-flight requests within [gracePeriodMillis] and force-stopping
     * after [timeoutMillis]. Analog of Go's `Shutdown(ctx)`.
     */
    public suspend fun shutdown(
        gracePeriodMillis: Long = DEFAULT_GRACE_PERIOD_MILLIS,
        timeoutMillis: Long = DEFAULT_SHUTDOWN_TIMEOUT_MILLIS,
    )

    /** Returns the router this server mounts. Direct port of Go's `Router()`. */
    public fun router(): Router

    public companion object {
        /** Default drain window on [shutdown]. */
        public const val DEFAULT_GRACE_PERIOD_MILLIS: Long = 5_000

        /** Default hard-stop deadline on [shutdown]. */
        public const val DEFAULT_SHUTDOWN_TIMEOUT_MILLIS: Long = 10_000
    }
}
