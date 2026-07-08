package com.primandproper.platform.server.http

import com.primandproper.platform.routing.Router
import com.primandproper.platform.routing.ktor.KtorRouter
import io.ktor.server.application.Application

/**
 * Mounts [router] onto this Ktor [Application]. This is the seam where "Ktor merges router and server
 * concerns Go keeps split" is resolved: Go's server mounts `router.Handler()` (an `http.Handler`);
 * Ktor has no universal handler type, so mounting is provider-specific. The injected `:routing-api`
 * [Router] must be the Ktor implementation — [KtorRouter] — whose `install` applies its observability
 * wiring and routes here.
 *
 * Factored out of [KtorHttpServer] so both the production Netty engine and a `testApplication` (which
 * binds no port) configure the application identically.
 *
 * TODO(server-tracing): platform-go additionally wraps the whole handler in `otelhttp` at the server
 *  boundary. Per-request observability is already wired inside [KtorRouter]; a server-level span would
 *  go here via a Ktor OpenTelemetry server plugin.
 */
public fun Application.configureHttpServer(router: Router) {
    val ktorRouter =
        router as? KtorRouter
            ?: error("server-ktor requires a :routing-ktor Router (KtorRouter); got ${router::class.qualifiedName}")
    ktorRouter.install(this)
}
