package com.primandproper.platform.server.http

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.routing.Router
import com.primandproper.platform.server.HttpServerConfig
import com.primandproper.platform.server.Server
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Default logger name when no service name is supplied. Port of `server/http.defaultLoggerName`. */
public const val DEFAULT_LOGGER_NAME: String = "api_server"

/**
 * The Ktor (Netty) HTTP server — port of platform-go's `server/http.server`. Mounts [router] onto a
 * Netty-backed Ktor `Application` (see [configureHttpServer]) and manages its lifecycle. The
 * [observer] instruments the server lifecycle (startup/shutdown), while per-request observability is
 * wired inside the router, mirroring how Go instruments at both the server (`otelhttp`) and router
 * (`otelchi`) layers.
 *
 * Build one with [HttpServer] (the analog of `ProvideHTTPServer`).
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
        // [config] is already defaulted (via its null timeout sentinels) and validated at construction.
        observer.logger.debug("setting up server")

        // TODO(tls): when config.tlsEnabled, add an sslConnector — Ktor's connector wants a Java
        //  KeyStore, so the PEM cert/key Go loads directly must first be converted into one.
        // TODO(startup-deadline): Ktor's start() does not bound the bind by config.startupDeadline;
        //  Go bounds its listener bind with that deadline.
        // The port lives on the engine connector here (rather than the simpler port= overload) because
        // that overload has no engine-configure hook — and the configure block is where Netty's timeout
        // knobs are set. See applyTimeouts.
        val server =
            embeddedServer(
                Netty,
                configure = {
                    connectors.add(EngineConnectorBuilder().apply { port = config.port })
                    applyTimeouts(config)
                },
            ) {
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
public fun HttpServer(
    config: HttpServerConfig,
    router: Router,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    serviceName: String = "",
): Server {
    val loggerName = serviceName.ifEmpty { DEFAULT_LOGGER_NAME }
    val observer = Observer(loggerName, logger, tracerProvider)
    return KtorHttpServer(config, observer, router, serviceName)
}

/**
 * Applies [config]'s socket timeouts to the Netty engine — the wiring Go's `provideStdLibHTTPServer`
 * gets from `http.Server`'s `ReadTimeout`/`WriteTimeout`/`IdleTimeout`. Without it the timeouts are
 * validated then discarded, leaving reads/idle keep-alive connections unbounded (a slow-loris hole).
 *
 * Ktor's Netty engine exposes the read/write bounds directly ([requestReadTimeoutSeconds]/
 * [responseWriteTimeoutSeconds], whole seconds) but has no idle-timeout knob, so [idleTimeout] is
 * honored at the channel level: an [IdleStateHandler] fires once a connection goes quiet for that
 * long and [CloseOnIdleConnectionHandler] closes it, matching Go's `IdleTimeout`.
 */
internal fun NettyApplicationEngine.Configuration.applyTimeouts(config: HttpServerConfig) {
    requestReadTimeoutSeconds = config.readTimeout().wholeSecondsAtLeastOne()
    responseWriteTimeoutSeconds = config.writeTimeout().wholeSecondsAtLeastOne()
    tcpKeepAlive = true

    val idleSeconds = config.idleTimeout().wholeSecondsAtLeastOne().toLong()
    channelPipelineConfig = {
        // `this` is the connection's ChannelPipeline. Added at the front so idleness is judged on raw
        // connection traffic, ahead of Ktor's codecs.
        addFirst(
            IdleStateHandler(0, 0, idleSeconds, TimeUnit.SECONDS),
            CloseOnIdleConnectionHandler,
        )
    }
}

/**
 * Rounds a [Duration] down to whole seconds for Netty's second-granularity timeout knobs, but never
 * below 1: a sub-second (but positive) config must still bound the socket — 0 means "no timeout" to
 * Netty and would silently reopen the hole these timeouts close.
 */
private fun Duration.wholeSecondsAtLeastOne(): Int = inWholeSeconds.coerceAtLeast(1L).toInt()

/** Closes a connection once an [IdleStateHandler] reports it idle. Stateless, so safely shareable. */
@ChannelHandler.Sharable
private object CloseOnIdleConnectionHandler : ChannelInboundHandlerAdapter() {
    override fun userEventTriggered(
        ctx: ChannelHandlerContext,
        evt: Any,
    ) {
        if (evt is IdleStateEvent) ctx.close() else ctx.fireUserEventTriggered(evt)
    }
}
