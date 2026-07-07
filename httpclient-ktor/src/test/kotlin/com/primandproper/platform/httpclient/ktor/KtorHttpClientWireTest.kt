package com.primandproper.platform.httpclient.ktor

import com.primandproper.platform.httpclient.HttpClientConfig
import com.primandproper.platform.httpclient.HttpRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val config = HttpClientConfig().apply { ensureDefaults() }
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
}
