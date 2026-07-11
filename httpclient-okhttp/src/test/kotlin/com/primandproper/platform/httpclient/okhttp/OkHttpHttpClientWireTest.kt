package com.primandproper.platform.httpclient.okhttp

import com.primandproper.platform.httpclient.HttpClientConfig
import com.primandproper.platform.httpclient.HttpRequest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Wire tests over [MockWebServer]. WRITTEN-BUT-NOT-RUN: this machine has no JVM/Android toolchain, so
 * these have never been compiled or executed — they document intended behaviour and run once a
 * toolchain is available.
 */
class OkHttpHttpClientWireTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `executes a GET and reads the body`() =
        runTest {
            server.enqueue(MockResponse().setBody("hello").setResponseCode(200))
            val client = OkHttpHttpClient.create(HttpClientConfig(timeout = 5.seconds))

            val response = client.execute(HttpRequest.get(server.url("/greet").toString()))

            assertEquals(200, response.statusCode)
            assertEquals("hello", response.bodyAsText())
            client.close()
        }

    @Test
    fun `sends request headers`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))
            val client = OkHttpHttpClient.create(HttpClientConfig(timeout = 5.seconds))

            client.execute(
                HttpRequest.build {
                    url = server.url("/x").toString()
                    header("X-Trace", "abc")
                },
            )

            val recorded = server.takeRequest()
            assertEquals("abc", recorded.getHeader("X-Trace"))
            client.close()
        }
}
