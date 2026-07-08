package com.primandproper.platform.uploads.android

import com.primandproper.platform.httpclient.FakeHttpClient
import com.primandproper.platform.httpclient.HttpHeaders
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Drives the Android signed-URL uploader against a fake HTTP client — no device, no network. */
class SignedUrlUploaderTest {
    private val signedUrl = "https://bucket.s3.example.com/obj?X-Amz-Signature=abc123"

    @Test
    fun `upload sends a PUT with the body and content type`() =
        runTest {
            val http = FakeHttpClient { HttpResponse(statusCode = 200, headers = HttpHeaders.of("ETag" to "\"deadbeef\"")) }
            val uploader = SignedUrlUploader(http)

            val result = uploader.upload(signedUrl, "payload".toByteArray(), contentType = "text/plain")

            val sent = http.lastRequest!!
            assertEquals(HttpMethod.PUT, sent.method)
            assertEquals(signedUrl, sent.url)
            assertEquals("text/plain", sent.headers.first("content-type"))
            assertContentEquals("payload".toByteArray(), sent.body)
            assertEquals(200, result.statusCode)
            assertEquals("\"deadbeef\"", result.etag)
        }

    @Test
    fun `a non-2xx response throws with the status and body`() =
        runTest {
            val http = FakeHttpClient { HttpResponse(statusCode = 403, body = "AccessDenied".toByteArray()) }
            val uploader = SignedUrlUploader(http)

            val err = assertFailsWith<SignedUrlUploadException> { uploader.upload(signedUrl, "x".toByteArray()) }
            assertEquals(403, err.statusCode)
            assertEquals("AccessDenied", err.responseBody)
        }

    @Test
    fun `the input-stream overload streams the same bytes`() =
        runTest {
            val http = FakeHttpClient()
            val uploader = SignedUrlUploader(http)

            uploader.upload(signedUrl, ByteArrayInputStream("streamed".toByteArray()))

            assertContentEquals("streamed".toByteArray(), http.lastRequest!!.body)
        }

    @Test
    fun `the span records length and status but not the signed url`() =
        runTest {
            val obs = RecordingObserver()
            val http = FakeHttpClient { HttpResponse(statusCode = 200) }
            SignedUrlUploader(http, obs).upload(signedUrl, "abc".toByteArray())

            val op = obs.operations.last { it.name == "Upload" }
            assertEquals(3, op.spanValues["length"])
            assertEquals(200, op.spanValues["http.response.status_code"])
            // The signed URL (a bearer secret) must never be recorded on any pillar.
            assertTrue(op.observations.none { (it.value as? String)?.contains("X-Amz-Signature") == true })
            assertTrue(op.ended)
        }
}
