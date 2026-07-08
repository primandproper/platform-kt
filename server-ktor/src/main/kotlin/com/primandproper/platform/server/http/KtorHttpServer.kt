package com.primandproper.platform.server.http

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.routing.Router
import com.primandproper.platform.server.HttpServerConfig
import com.primandproper.platform.server.Server
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Default logger name when no service name is supplied. Port of `server/http.defaultLoggerName`. */
public const val DEFAULT_LOGGER_NAME: String = "api_server"

/**
 * The Ktor (Netty) HTTP server — port of platform-go's `server/http.server`. Mounts [router] onto a
 * Netty-backed Ktor `Application` (see [configureHttpServer]) and manages its lifecycle. The
 * [observer] instruments the server lifecycle (startup/shutdown), while per-request observability is
 * wired inside the router, mirroring how Go instruments at both the server (`otelhttp`) and router
 * (`otelchi`) layers.
 *
 * Build one with [provideHttpServer] (the analog of `ProvideHTTPServer`).
 */
public class KtorHttpServer internal constructor(
    private val config: HttpServerConfig,
    private val observer: Observer,
    private val router: Router,
    private val serviceName: String,
) : Server {
    private var engine: EmbeddedServer<*, *>? = null

    override fun router(): Router = router

    override suspend fun serve() {
        config.ensureDefaults()
        config.validate()
        observer.logger.debug("setting up server")

        // TODO(tls): when config.tlsEnabled, add an sslConnector — Ktor's connector wants a Java
        //  KeyStore, so the PEM cert/key Go loads directly must first be converted into one.
        // TODO(startup-deadline): Ktor's start() does not bound the bind by config.startupDeadline;
        //  Go bounds its listener bind with that deadline.
        val server =
            embeddedServer(Netty, port = config.port) {
                configureHttpServer(router)
            }
        engine = server

        observer.logger
            .withName(serviceName.ifEmpty { DEFAULT_LOGGER_NAME })
            .withValue("port", config.port)
            .info("listening for HTTP requests")

        // Non-blocking: see the Server.serve() doc on the divergence from Go's blocking Serve().
        server.start(wait = false)
    }

    override suspend fun shutdown(
        gracePeriodMillis: Long,
        timeoutMillis: Long,
    ) {
        // TODO(trace-flush): flush the tracer provider before stopping (Go's Shutdown drains then
        //  ForceFlushes) once observability-api exposes a flush hook.
        engine?.stop(gracePeriodMillis, timeoutMillis)
        engine = null
    }
}

/**
 * Builds a [Server] instance. Direct port of platform-go's `ProvideHTTPServer`: [serviceName], when
 * non-empty, names the server's logger; otherwise [DEFAULT_LOGGER_NAME] is used. The [logger] and
 * [tracerProvider] are bundled into the server's [Observer], the same pillars Go threads in.
 */
public fun provideHttpServer(
    config: HttpServerConfig,
    router: Router,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    serviceName: String = "",
): Server {
    val loggerName = serviceName.ifEmpty { DEFAULT_LOGGER_NAME }
    val observer = Observer(loggerName, logger, tracerProvider)
    return KtorHttpServer(config, observer, router, serviceName)
}
