package com.primandproper.platform.httpclient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRequestTest {
    @Test
    fun `builder assembles method, url, headers and body`() {
        val request =
            HttpRequest.build {
                method = HttpMethod.POST
                url = "https://api.example.com/v1/things"
                header("Content-Type", "application/json")
                header("Authorization", "Bearer t")
                body("{}")
            }

        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://api.example.com/v1/things", request.url)
        assertEquals("application/json", request.headers.first("content-type"))
        assertEquals("{}", request.body?.toString(Charsets.UTF_8))
    }

    @Test
    fun `headers are case-insensitive and multi-valued`() {
        val headers =
            HttpHeaders.build {
                add("Set-Cookie", "a=1")
                add("set-cookie", "b=2")
            }
        assertEquals(listOf("a=1", "b=2"), headers["Set-Cookie"])
        assertTrue(headers.contains("SET-COOKIE"))
    }

    @Test
    fun `get factory has no body`() {
        val request = HttpRequest.get("https://example.com")
        assertEquals(HttpMethod.GET, request.method)
        assertNull(request.body)
    }
}
