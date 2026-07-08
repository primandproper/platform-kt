package com.primandproper.platform.routing.ktor

import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.routing.RouterSettings
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the request-ID middleware ported from platform-go's `routing/chi/request_id.go` /
 * `request_id_test.go`: an incoming `X-Request-ID` is honored, and one is generated otherwise.
 */
class RequestIdTest {
    private fun router() = KtorRouter(noopObserver("router"), RouterSettings(serviceName = "test"))

    @Test
    fun `honors an incoming request id header`() =
        testApplication {
            val r = router()
            r.get("/id") { call -> call.respondText(200, call.requestId ?: "") }
            application { r.install(this) }

            val resp = client.get("/id") { header(REQUEST_ID_HEADER, "abc-123") }

            assertEquals("abc-123", resp.bodyAsText())
        }

    @Test
    fun `generates a request id when none is supplied`() =
        testApplication {
            val r = router()
            r.get("/id") { call -> call.respondText(200, call.requestId ?: "") }
            application { r.install(this) }

            val id = client.get("/id").bodyAsText()

            assertTrue(id.isNotBlank(), "a request id should have been generated")
        }
}
