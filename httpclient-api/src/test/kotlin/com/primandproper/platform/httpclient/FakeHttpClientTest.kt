package com.primandproper.platform.httpclient

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeHttpClientTest {
    @Test
    fun `records requests and returns the canned response`() =
        runTest {
            val client = FakeHttpClient { HttpResponse(statusCode = 201, body = "ok".toByteArray()) }

            val response = client.execute(HttpRequest.post("https://example.com/things", "{}".toByteArray()))

            assertEquals(201, response.statusCode)
            assertEquals("ok", response.bodyAsText())
            assertEquals(1, client.requests.size)
            assertEquals("https://example.com/things", client.lastRequest?.url)
        }

    @Test
    fun `noop client returns an empty 200`() =
        runTest {
            val response = NoopHttpClient.execute(HttpRequest.get("https://example.com"))
            assertEquals(200, response.statusCode)
            assertEquals(0, response.body.size)
        }
}
