package com.primandproper.platform.server.http

import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.routing.RouterSettings
import com.primandproper.platform.routing.RoutingConfig
import com.primandproper.platform.routing.RoutingProvider
import com.primandproper.platform.routing.ktor.KtorRouter
import com.primandproper.platform.routing.ktor.provideRouter
import com.primandproper.platform.server.HttpServerConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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
            val router = provideRouter(RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "svc")))
            router.get("/hi") { call -> call.respondText(200, "hi") }
            application { configureHttpServer(router) }

            assertEquals("hi", client.get("/hi").bodyAsText())
        }

    @Test
    fun `provideHttpServer exposes the mounted router`() {
        val router = provideRouter(RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "svc")))
        val server = provideHttpServer(config(), router, serviceName = "svc")

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
}
