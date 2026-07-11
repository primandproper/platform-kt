package com.primandproper.platform.routing.ktor

import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.routing.DefaultRouteParamManager
import com.primandproper.platform.routing.HttpHandler
import com.primandproper.platform.routing.HttpMethod
import com.primandproper.platform.routing.Middleware
import com.primandproper.platform.routing.Route
import com.primandproper.platform.routing.RouterSettings
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire tests for [KtorRouter] over Ktor's `testApplication` (from `ktor-server-test-host`), so no
 * real port is bound. Covers the route registration, middleware, subrouter, recovery, and
 * observability behaviors ported from platform-go's `routing/chi/router_test.go`,
 * `middleware_test.go`, and `recovery_test.go`.
 *
 * WRITTEN-BUT-NOT-RUN: no JVM toolchain on this machine, so these have never been compiled or run.
 */
class KtorRouterTest {
    private fun router(observer: com.primandproper.platform.observability.Observer = noopObserver("router")) =
        KtorRouter(observer, RouterSettings(serviceName = "test"))

    @Test
    fun `registers and serves a GET route`() =
        testApplication {
            val r = router()
            r.get("/ping") { call -> call.respondText(200, "pong") }
            application { r.install(this) }

            val resp = client.get("/ping")

            assertEquals(200, resp.status.value)
            assertEquals("pong", resp.bodyAsText())
        }

    @Test
    fun `extracts a path parameter`() =
        testApplication {
            val r = router()
            r.get("/things/{id}") { call ->
                val fetch = DefaultRouteParamManager.buildRouteParamStringIDFetcher("id")
                call.respondText(200, fetch(call))
            }
            application { r.install(this) }

            assertEquals("42", client.get("/things/42").bodyAsText())
        }

    @Test
    fun `applies middleware outermost-first`() =
        testApplication {
            val order = mutableListOf<String>()
            val first: Middleware = { next ->
                HttpHandler { call ->
                    order += "first"
                    next.handle(call)
                }
            }
            val second: Middleware = { next ->
                HttpHandler { call ->
                    order += "second"
                    next.handle(call)
                }
            }

            val r = router()
            r.addRoute(
                HttpMethod.GET,
                "/wrapped",
                HttpHandler { call ->
                    order += "handler"
                    call.respondText(200, "ok")
                },
                first,
                second,
            )
            application { r.install(this) }

            client.get("/wrapped")

            assertEquals(listOf("first", "second", "handler"), order)
        }

    @Test
    fun `mounts a subrouter under a prefix`() =
        testApplication {
            val r = router()
            r.route("/api") { sub -> sub.get("/health") { call -> call.respondText(200, "up") } }
            application { r.install(this) }

            assertEquals("up", client.get("/api/health").bodyAsText())
            assertTrue(r.routes().contains(Route(HttpMethod.GET, "/api/health")))
        }

    @Test
    fun `middleware added via withMiddleware wraps routes registered inside a route block`() =
        testApplication {
            // Regression: route() must pass the router's ambient middleware into the subrouter, so
            // middleware added via withMiddleware() still wraps routes registered inside route(){}.
            val ran = mutableListOf<String>()
            val mw: Middleware = { next ->
                HttpHandler { call ->
                    ran += "mw"
                    next.handle(call)
                }
            }

            val r = router()
            r.withMiddleware(mw).route("/admin") { sub ->
                sub.get("/users") { call ->
                    ran += "handler"
                    call.respondText(200, "ok")
                }
            }
            application { r.install(this) }

            val resp = client.get("/admin/users")

            assertEquals(200, resp.status.value)
            // Before the fix the subrouter dropped the ambient middleware, so only "handler" ran.
            assertEquals(listOf("mw", "handler"), ran)
        }

    @Test
    fun `handle matches any method`() =
        testApplication {
            val r = router()
            r.handle("/any") { call -> call.respondText(200, call.method.name) }
            application { r.install(this) }

            assertEquals("GET", client.get("/any").bodyAsText())
            assertEquals("POST", client.post("/any").bodyAsText())
        }

    @Test
    fun `recovers a throwing handler into a 500 and records the error`() =
        testApplication {
            val recording = RecordingObserver()
            val r = router(recording)
            r.get("/boom") { throw RuntimeException("handler blew up") }
            application { r.install(this) }

            val resp = client.get("/boom")

            assertEquals(500, resp.status.value)
            val op = recording.operations.first { it.values["http.path"] == "/boom" }
            assertTrue(op.errors.isNotEmpty(), "expected the recovered error to be recorded")
            assertTrue(op.ended, "the operation must be ended")
        }

    @Test
    fun `instruments a request with method and path`() =
        testApplication {
            val recording = RecordingObserver()
            val r = router(recording)
            r.get("/observed") { call -> call.respondStatus(204) }
            application { r.install(this) }

            client.get("/observed")

            recording.assertObservedOperationWithValues("http.method" to "GET", "http.path" to "/observed")
        }

    @Test
    fun `records the response status code on the request span`() =
        testApplication {
            val recording = RecordingObserver()
            val r = router(recording)
            r.get("/observed") { call -> call.respondStatus(204) }
            application { r.install(this) }

            client.get("/observed")

            val op = recording.operations.first { it.values["http.path"] == "/observed" }
            assertEquals(204, op.values["http.status_code"])
        }

    @Test
    fun `records the status code for a non-throwing 5xx without recording an exception`() =
        testApplication {
            val recording = RecordingObserver()
            val r = router(recording)
            // The handler *returns* a 5xx rather than throwing, so nothing goes through acknowledge.
            r.get("/degraded") { call -> call.respondStatus(503) }
            application { r.install(this) }

            val resp = client.get("/degraded")

            assertEquals(503, resp.status.value)
            val op = recording.operations.first { it.values["http.path"] == "/degraded" }
            assertEquals(503, op.values["http.status_code"])
            // The span is marked ERROR via setStatus (not acknowledge), so no exception is recorded.
            assertTrue(op.errors.isEmpty(), "a returned 5xx is not an exception")
            assertTrue(op.ended)
        }

    @Test
    fun `does not instrument health checks`() =
        testApplication {
            val recording = RecordingObserver()
            val r = router(recording)
            r.get("/_ops_/live") { call -> call.respondStatus(200) }
            application { r.install(this) }

            val resp = client.get("/_ops_/live")

            assertEquals(200, resp.status.value)
            assertTrue(recording.operations.none { it.values["http.path"] == "/_ops_/live" })
        }
}
