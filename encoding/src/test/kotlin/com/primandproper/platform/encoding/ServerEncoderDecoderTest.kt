package com.primandproper.platform.encoding

import com.primandproper.platform.observability.testing.RecordingObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `server_encoder_decoder_test.go`. */
class ServerEncoderDecoderTest {
    private fun recording(contentType: ContentType): Pair<ServerEncoderDecoder, RecordingObserver> {
        val obs = RecordingObserver()
        return DefaultServerEncoderDecoder(contentType, obs) to obs
    }

    @Test
    fun `encodeResponse writes JSON body, header and status`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.encodeResponseWithStatus(res, Example.serializer(), Example("name"), HTTP_STATUS_OK)

        // Go emits a trailing newline via json.Encoder; kotlinx.serialization does not (see divergence note).
        assertEquals("""{"name":"name"}""", res.bodyString)
        assertEquals("application/json", res.headers[CONTENT_TYPE_HEADER_KEY])
        assertEquals(HTTP_STATUS_OK, res.statusCode)
    }

    @Test
    fun `encodeResponse honors the configured content type without a pre-set header`() {
        // The configured (JSON) encoder must win and set the header itself, even though the writer
        // starts with no header — faithful to Go's "honors configured content type" case.
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.encodeResponseWithStatus(res, Example.serializer(), Example("name"), HTTP_STATUS_OK)

        assertEquals("application/json", res.headers[CONTENT_TYPE_HEADER_KEY])
    }

    @Test
    fun `encodeResponse observes the response status`() {
        val (ed, obs) = recording(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.encodeResponseWithStatus(res, Example.serializer(), Example("name"), HTTP_STATUS_OK)

        obs.assertObservedOperationWithValues("http.response.status_code" to HTTP_STATUS_OK)
    }

    @Test
    fun `encodeResponse with a broken payload leaves an empty body but still observes status and acknowledges`() {
        val (ed, obs) = recording(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.encodeResponseWithStatus(res, BrokenSerializer, Example("name"), HTTP_STATUS_OK)

        assertEquals("", res.bodyString)
        // Even though encoding failed, the status was observed and the failure acknowledged on the op.
        val op = obs.operations.first { it.name == "EncodeResponse" }
        assertEquals(HTTP_STATUS_OK, op.values["http.response.status_code"])
        assertEquals(1, op.errors.size)
    }

    @Test
    fun `encodeResponseWithStatus writes an arbitrary status`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.encodeResponseWithStatus(res, Example.serializer(), Example("name"), 666)

        assertEquals(666, res.statusCode)
    }

    @Test
    fun `respondWithData writes HTTP 200`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val res = RecordingResponseWriter()

        ed.respondWithData(res, Example.serializer(), Example("data"))

        assertEquals(HTTP_STATUS_OK, res.statusCode)
        assertTrue(res.bodyString.isNotEmpty())
    }

    @Test
    fun `mustEncodeJson round-trips a value`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val bytes = ed.mustEncodeJson(Example.serializer(), Example("standard"))
        assertEquals("""{"name":"standard"}""", bytes.decodeToString())
    }

    @Test
    fun `mustEncodeJson throws on a broken payload`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        assertFailsWith<Exception> { ed.mustEncodeJson(BrokenSerializer, Example("boom")) }
    }

    @Test
    fun `mustEncode round-trips JSON`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        assertEquals("""{"name":"json"}""", ed.mustEncode(Example.serializer(), Example("json")).decodeToString())
    }

    @Test
    fun `mustEncode throws on a broken payload`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        assertFailsWith<Exception> { ed.mustEncode(BrokenSerializer, Example("boom")) }
    }

    @Test
    fun `decodeBytes round-trips JSON and observes the length`() {
        val (ed, obs) = recording(ContentType.JSON)
        val out = ed.decodeBytes("""{"name":"name"}""".encodeToByteArray(), Example.serializer())

        assertEquals(Example("name"), out)
        val op = obs.operations.first { it.name == "DecodeBytes" }
        assertEquals(15, op.values["length"])
        assertEquals("application/json", op.values["content_type"])
    }

    @Test
    fun `decodeRequest negotiates the format from the request header`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val req = FakeRequest(contentType = "application/json", payload = """{"name":"name"}""".encodeToByteArray())

        assertEquals(Example("name"), ed.decodeRequest(req, Example.serializer()))
    }

    @Test
    fun `decodeRequest with a missing header defaults to JSON`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val req = FakeRequest(contentType = null, payload = """{"name":"name"}""".encodeToByteArray())

        assertEquals(Example("name"), ed.decodeRequest(req, Example.serializer()))
    }

    @Test
    fun `decodeRequest still decodes when closing the body fails`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val data = """{"name":"test"}""".encodeToByteArray()
        val req = FakeRequest(contentType = "application/json", payload = data, stream = CloseFailingStream(data))

        // The close error is only logged; the decode result stands. Faithful to Go's deferred Close.
        assertEquals(Example("test"), ed.decodeRequest(req, Example.serializer()))
    }

    @Test
    fun `decodeBytes rejects unknown fields, mirroring DisallowUnknownFields`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        assertFailsWith<Exception> {
            ed.decodeBytes("""{"name":"name","extra":true}""".encodeToByteArray(), Example.serializer())
        }
    }

    @Test
    fun `reified conveniences derive the serializer`() {
        val ed = ServerEncoderDecoder(ContentType.JSON)
        val bytes = ed.mustEncode(Example("reified"))
        assertEquals(Example("reified"), ed.decodeBytes<Example>(bytes))
    }

    @Test
    fun `every non-JSON format is a documented TODO seam`() {
        for (ct in listOf(ContentType.XML, ContentType.TOML, ContentType.YAML, ContentType.EMOJI)) {
            val ed = ServerEncoderDecoder(ct)
            // decodeBytes surfaces the seam directly; mustEncode wraps it (the analog of Go's Wrapf
            // feeding the panicker), so its cause is the UnsupportedContentFormatException.
            assertFailsWith<UnsupportedContentFormatException> {
                ed.decodeBytes("x".encodeToByteArray(), Example.serializer())
            }
            val wrapped = assertFailsWith<Exception> { ed.mustEncode(Example.serializer(), Example("x")) }
            assertTrue(wrapped is UnsupportedContentFormatException || wrapped.cause is UnsupportedContentFormatException)
        }
    }
}
