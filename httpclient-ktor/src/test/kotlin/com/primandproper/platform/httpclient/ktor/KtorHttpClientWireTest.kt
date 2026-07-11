package com.primandproper.platform.httpclient.ktor

import com.primandproper.platform.httpclient.HttpClientConfig
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.HttpClient as KtorClient

/**
 * Wire tests over Ktor's [MockEngine]. WRITTEN-BUT-NOT-RUN: no JVM toolchain on this machine, so
 * these have never been compiled or executed. They construct the client over the internal
 * constructor with a mock engine (the public `create` uses a real CIO engine).
 */
class KtorHttpClientWireTest {
    private fun clientReturning(
        status: HttpStatusCode,
        body: String,
    ): KtorHttpClient {
        val engine =
            MockEngine { _ ->
                respond(
                    content = ByteReadChannel(body.toByteArray()),
                    status = status,
                    headers = headersOf("Content-Type", "text/plain"),
                )
            }
        val config = HttpClientConfig()
        return KtorHttpClient(KtorClient(engine), config)
    }

    @Test
    fun `executes a GET and reads the body`() =
        runTest {
            val client = clientReturning(HttpStatusCode.OK, "hello")

            val response = client.execute(HttpRequest.get("https://example.com/greet"))

            assertEquals(200, response.statusCode)
            assertEquals("hello", response.bodyAsText())
            client.close()
        }

    private fun capturingClient(capture: (HttpRequestData) -> Unit): KtorHttpClient {
        val engine =
            MockEngine { request ->
                capture(request)
                respond(
                    content = ByteReadChannel(ByteArray(0)),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "text/plain"),
                )
            }
        val config = HttpClientConfig()
        return KtorHttpClient(KtorClient(engine), config)
    }

    @Test
    fun `skips the headers Ktor manages so verbatim forwarding does not trip validateHeaders`() =
        runTest {
            var captured: HttpRequestData? = null
            val client = capturingClient { captured = it }

            val request =
                HttpRequest.build {
                    method = HttpMethod.POST
                    url = "https://example.com/upload"
                    header("Transfer-Encoding", "chunked")
                    header("Upgrade", "websocket")
                    header("Content-Length", "3")
                    header("X-Custom", "keep")
                    body("abc")
                }

            // Would throw UnsafeHeaderException before the fix (Transfer-Encoding/Upgrade are rejected).
            val response = client.execute(request)

            assertEquals(200, response.statusCode)
            val headers = captured!!.headers
            assertNull(headers["Transfer-Encoding"], "Transfer-Encoding must not be forwarded")
            assertNull(headers["Upgrade"], "Upgrade must not be forwarded")
            assertEquals("keep", headers["X-Custom"], "unmanaged headers still pass through")
            client.close()
        }

    @Test
    fun `forwards Content-Type onto the body when a body is present`() =
        runTest {
            var captured: HttpRequestData? = null
            val client = capturingClient { captured = it }

            val request =
                HttpRequest.build {
                    method = HttpMethod.POST
                    url = "https://example.com/json"
                    header("Content-Type", "application/json")
                    body("{}")
                }

            client.execute(request)

            // Matches OkHttp, which types the body from the Content-Type header.
            assertEquals("application/json", captured!!.body.contentType?.toString())
            client.close()
        }

    @Test
    fun `drops a bodiless Content-Type header`() =
        runTest {
            var captured: HttpRequestData? = null
            val client = capturingClient { captured = it }

            val request =
                HttpRequest.build {
                    method = HttpMethod.GET
                    url = "https://example.com/thing"
                    header("Content-Type", "application/json")
                }

            client.execute(request)

            assertNull(captured!!.headers["Content-Type"], "a Content-Type with no body is meaningless")
            assertNull(captured!!.body.contentType)
            client.close()
        }
}
