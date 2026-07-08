package com.primandproper.platform.routing.ktor

import com.primandproper.platform.observability.Observer
import com.primandproper.platform.routing.HttpHandler
import com.primandproper.platform.routing.Middleware
import com.primandproper.platform.routing.MountableHandler
import com.primandproper.platform.routing.Route
import com.primandproper.platform.routing.Router
import com.primandproper.platform.routing.RouterSettings
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.server.routing.Route as KtorRoute

/**
 * The Ktor-backed [Router] — the analog of platform-go's `routing/chi.router`. It implements the
 * `:routing-api` contract over Ktor's routing engine and, on [install], wires the observability
 * [Observer] into per-request recovery + logging (the two middlewares Go's chi mux builds with the
 * `Observer`).
 *
 * ## Preserving the router/server split
 * Go builds a standalone `http.Handler` the server later mounts. Ktor declares routes onto a running
 * `Application`, so this router cannot be a free-standing handler — instead it *accumulates* route
 * registrations and observability wiring, then applies them all in [install]. The server backend
 * (`:server-ktor`) calls [install]; tests mount it directly onto a `testApplication`. [handler]
 * returns `this` as the opaque [MountableHandler], exactly as Go's `Handler()` hands back its mux.
 *
 * ## Divergences from Go, documented where they bite
 * - Metrics: Go's chi mux installs otelchi metric middlewares. observability-api has no metrics
 *   pillar, so those are a `TODO(metrics)` seam, matching `:cache-api`.
 * - CORS: Go installs go-chi/cors from [RouterSettings]. Enforcement here is a `TODO(cors)` seam
 *   (would use `ktor-server-cors`); the settings are carried and validated regardless.
 * - `http.ErrAbortHandler`: Go's recovery re-panics on it to sever the connection. Ktor has no such
 *   sentinel; [CancellationException] is re-thrown (never swallowed) as the nearest analog, so cancels
 *   propagate while genuine failures are recovered into a 500.
 */
public class KtorRouter internal constructor(
    private val observer: Observer,
    private val settings: RouterSettings,
    private val ambientMiddleware: List<Middleware>,
    private val registrations: MutableList<KtorRoute.() -> Unit>,
    private val routeList: MutableList<Route>,
) : Router, MountableHandler {
    /** Builds a fresh router for [settings], instrumented by [observer]. */
    public constructor(observer: Observer, settings: RouterSettings) :
        this(observer, settings, emptyList(), mutableListOf(), mutableListOf())

    override fun routes(): List<Route> = routeList.toList()

    override fun handler(): MountableHandler = this

    override fun withMiddleware(vararg middleware: Middleware): Router =
        // Shares the registration and route lists (like chi's `With`, which shares the routing tree),
        // so routes added through the returned router land here — just wrapped in the extra middleware.
        KtorRouter(observer, settings, ambientMiddleware + middleware, registrations, routeList)

    override fun route(
        pattern: String,
        fn: (Router) -> Unit,
    ): Router {
        // The subrouter inherits this router's ambient middleware so that middleware added via
        // withMiddleware() still wraps every route registered inside the subrouter (auth/logging/etc.);
        // it gets fresh registration/route lists because route() collects them under the `pattern` mount.
        val child = KtorRouter(observer, settings, ambientMiddleware, mutableListOf(), mutableListOf())
        fn(child)
        child.routeList.forEach { routeList += Route(it.method, joinPaths(pattern, it.path)) }
        registrations += {
            route(pattern) {
                child.registrations.forEach { it(this) }
            }
        }
        return this
    }

    override fun handle(
        pattern: String,
        handler: HttpHandler,
    ) {
        val mws = ambientMiddleware
        routeList += Route(ANY_METHOD, pattern)
        registrations += {
            route(pattern) {
                handle { runComposed(handler, mws, call) }
            }
        }
    }

    override fun handleFunc(
        pattern: String,
        handler: HttpHandler,
    ): Unit = handle(pattern, handler)

    override fun connect(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("CONNECT", pattern, handler, emptyList())

    override fun delete(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("DELETE", pattern, handler, emptyList())

    override fun get(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("GET", pattern, handler, emptyList())

    override fun head(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("HEAD", pattern, handler, emptyList())

    override fun options(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("OPTIONS", pattern, handler, emptyList())

    override fun patch(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("PATCH", pattern, handler, emptyList())

    override fun post(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("POST", pattern, handler, emptyList())

    override fun put(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("PUT", pattern, handler, emptyList())

    override fun trace(
        pattern: String,
        handler: HttpHandler,
    ): Unit = register("TRACE", pattern, handler, emptyList())

    override fun addRoute(
        method: String,
        path: String,
        handler: HttpHandler,
        vararg middleware: Middleware,
    ): Unit = register(method, path, handler, middleware.toList())

    private fun register(
        method: String,
        path: String,
        handler: HttpHandler,
        extra: List<Middleware>,
    ) {
        val normalized = method.trim().uppercase()
        require(normalized in VALID_METHODS) { "invalid method: $method" }
        val mws = ambientMiddleware + extra
        routeList += Route(normalized, path)
        val ktorMethod = HttpMethod.parse(normalized)
        registrations += {
            route(path, ktorMethod) {
                handle { runComposed(handler, mws, call) }
            }
        }
    }

    /** Composes [mws] around [handler] (first middleware outermost, per Go/chi) and runs it. */
    private suspend fun runComposed(
        handler: HttpHandler,
        mws: List<Middleware>,
        call: ApplicationCall,
    ) {
        val composed = mws.foldRight(handler) { mw, next -> mw(next) }
        composed.handle(KtorRoutingCall(call))
    }

    /**
     * Applies this router's request-ID + observability wiring and all accumulated routes onto
     * [application]. Called once by the server backend (or a `testApplication`). Analog of the server
     * mounting Go's `router.Handler()`.
     */
    public fun install(application: Application) {
        installRequestId(application)
        installObservability(application)
        // TODO(cors): enforce settings.validDomains / settings.enableCORSForLocalhost here via
        //  ktor-server-cors, mirroring the go-chi/cors handler Go's buildChiMux installs.
        application.routing {
            registrations.forEach { it(this) }
        }
    }

    private fun installRequestId(application: Application) {
        application.intercept(ApplicationCallPipeline.Setup) {
            val existing = call.request.header(REQUEST_ID_HEADER)
            val id = if (!existing.isNullOrBlank()) existing else generateRequestId()
            call.attributes.put(RequestIdKey, id)
        }
    }

    /**
     * The combined recovery + logging middleware, wiring the [Observer] exactly where Go's chi mux
     * does: a per-request operation is begun (skipped for health checks), the method/path/request-ID
     * are recorded, panics are recovered into a 500, and the served status/elapsed are logged unless
     * silenced. Go keeps recovery and logging as two middlewares; they are one interceptor here
     * because Ktor gives a single pipeline hook that both need to straddle.
     */
    private fun installObservability(application: Application) {
        application.intercept(ApplicationCallPipeline.Monitoring) {
            val path = call.request.path()
            if (isHealthCheck(path)) {
                proceed()
                return@intercept
            }

            val op = observer.begin(SPAN_NAME)
            op.set("http.method", call.request.httpMethod.value)
            op.set("http.path", path)
            op.set("http.request_id", requestIdOf(call))
            val startNanos = System.nanoTime()
            try {
                proceed()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                op.acknowledge(failure, "recovering from panic in HTTP handler")
                try {
                    call.respond(HttpStatusCode.InternalServerError)
                } catch (_: Throwable) {
                    // The response was already (partly) written; nothing more we can safely do.
                }
            } finally {
                if (!settings.silenceRouteLogging) {
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                    op.logger
                        .withValue("status", call.response.status()?.value)
                        .withValue("elapsed", elapsedMs)
                        .info("response served")
                }
                op.end()
            }
        }
    }

    private companion object {
        const val SPAN_NAME = "http_request"

        /** The pseudo-method [routes] records for [handle]/[handleFunc], which match every verb. */
        const val ANY_METHOD = "*"

        val VALID_METHODS =
            setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "CONNECT", "OPTIONS", "TRACE")

        /** Joins a subrouter prefix with a child path for the [routes] listing, collapsing a doubled slash. */
        fun joinPaths(
            prefix: String,
            child: String,
        ): String =
            when {
                prefix.endsWith("/") && child.startsWith("/") -> prefix + child.removePrefix("/")
                else -> prefix + child
            }
    }
}
