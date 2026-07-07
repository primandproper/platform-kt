package com.primandproper.platform.httpclient.okhttp

import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Pure request-building tests — no server or network required. */
class OkHttpMappingTest {
    @Test
    fun `maps method, url and headers`() {
        val request =
            HttpRequest.build {
                method = HttpMethod.POST
                url = "https://api.example.com/v1/things"
                header("Content-Type", "application/json")
                header("X-Trace", "abc")
                body("{}")
            }

        val okRequest = request.toOkHttpRequest()

        assertEquals("POST", okRequest.method)
        assertEquals("https://api.example.com/v1/things", okRequest.url.toString())
        assertEquals("abc", okRequest.header("X-Trace"))
        assertNotNull(okRequest.body)
    }

    @Test
    fun `GET has no request body`() {
        val okRequest = HttpRequest.get("https://example.com").toOkHttpRequest()
        assertEquals("GET", okRequest.method)
        assertNull(okRequest.body)
    }

    @Test
    fun `POST without a body still gets an empty body`() {
        val okRequest = HttpRequest(HttpMethod.POST, "https://example.com").toOkHttpRequest()
        assertNotNull(okRequest.body)
    }
}
