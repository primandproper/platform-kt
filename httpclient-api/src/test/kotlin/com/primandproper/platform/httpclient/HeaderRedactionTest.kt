package com.primandproper.platform.httpclient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderRedactionTest {
    @Test
    fun `sensitive headers are recognised case-insensitively`() {
        assertTrue(HeaderRedaction.isSensitive("Authorization"))
        assertTrue(HeaderRedaction.isSensitive("authorization"))
        assertTrue(HeaderRedaction.isSensitive("COOKIE"))
        assertTrue(HeaderRedaction.isSensitive("Set-Cookie"))
        assertTrue(HeaderRedaction.isSensitive("Proxy-Authorization"))
        assertFalse(HeaderRedaction.isSensitive("Content-Type"))
        assertFalse(HeaderRedaction.isSensitive("Accept"))
    }

    @Test
    fun `redactValues masks sensitive values and preserves count`() {
        val redacted = HeaderRedaction.redactValues("set-cookie", listOf("a=1", "b=2"))
        assertEquals(listOf(HeaderRedaction.PLACEHOLDER, HeaderRedaction.PLACEHOLDER), redacted)
    }

    @Test
    fun `redactValues leaves non-sensitive values untouched`() {
        val values = listOf("application/json")
        assertEquals(values, HeaderRedaction.redactValues("content-type", values))
    }

    @Test
    fun `redact masks only the sensitive headers in a map`() {
        val headers =
            HttpHeaders.build {
                add("Authorization", "Bearer secret-token")
                add("Content-Type", "application/json")
                add("Cookie", "session=abc")
            }

        val redacted = HeaderRedaction.redact(headers)

        assertEquals(listOf(HeaderRedaction.PLACEHOLDER), redacted["authorization"])
        assertEquals(listOf(HeaderRedaction.PLACEHOLDER), redacted["cookie"])
        assertEquals(listOf("application/json"), redacted["content-type"])
        // The secret never appears anywhere in the redacted view.
        assertFalse(redacted.values.flatten().any { it.contains("secret-token") })
    }
}
