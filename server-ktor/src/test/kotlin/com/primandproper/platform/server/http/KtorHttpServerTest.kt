package com.primandproper.platform.server.http

import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.routing.RouterSettings
import com.primandproper.platform.routing.RoutingConfig
import com.primandproper.platform.routing.RoutingProvider
import com.primandproper.platform.routing.ktor.KtorRouter
import com.primandproper.platform.routing.ktor.Router
import com.primandproper.platform.server.HttpServerConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Covers mounting and lifecycle wiring from platform-go's `server/http/http_server_test.go`, over
 * Ktor's `testApplication` so no real port is bound.
 *
 * WRITTEN-BUT-NOT-RUN: no JVM toolchain on this machine, so these have never been compiled or run.
 */
class KtorHttpServerTest {
    private fun config() = HttpServerConfig(port = 8080, startupDeadline = 5.seconds)

    @Test
    fun `configureHttpServer mounts the routing-ktor router`() =
        testApplication {
            val router = Router(RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "svc")))
            router.get("/hi") { call -> call.respondText(200, "hi") }
            application { configureHttpServer(router) }

            assertEquals("hi", client.get("/hi").bodyAsText())
        }

    @Test
    fun `HttpServer exposes the mounted router`() {
        val router = Router(RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "svc")))
        val server = HttpServer(config(), router, serviceName = "svc")

        assertSame(router, server.router())
    }

    @Test
    fun `per-request observability flows through the mounted server`() =
        testApplication {
            val recording = RecordingObserver()
            val router = KtorRouter(recording, RouterSettings(serviceName = "svc"))
            router.get("/svc") { call -> call.respondStatus(200) }
            application { configureHttpServer(router) }

            client.get("/svc")

            recording.assertObservedOperationWithValues("http.method" to "GET", "http.path" to "/svc")
        }

    @Test
    fun `applyTimeouts wires the configured timeouts into the Netty engine`() {
        val engineConfig = NettyApplicationEngine.Configuration()

        engineConfig.applyTimeouts(
            HttpServerConfig(
                port = 8080,
                startupDeadline = 5.seconds,
                readTimeout = 3.seconds,
                writeTimeout = 7.seconds,
                idleTimeout = 11.seconds,
            ),
        )

        assertEquals(3, engineConfig.requestReadTimeoutSeconds)
        assertEquals(7, engineConfig.responseWriteTimeoutSeconds)
        assertTrue(engineConfig.tcpKeepAlive)
        // idleTimeout has no engine-level knob, so it is honored via the channel pipeline.
        assertNotNull(engineConfig.channelPipelineConfig)
    }
}
