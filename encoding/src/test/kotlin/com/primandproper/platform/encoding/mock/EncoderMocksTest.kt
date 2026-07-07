package com.primandproper.platform.encoding.mock

import com.primandproper.platform.encoding.Example
import com.primandproper.platform.encoding.HTTP_STATUS_OK
import com.primandproper.platform.encoding.RecordingResponseWriter
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Port of the usage the moq-generated `mockencoding` mocks support. */
class EncoderMocksTest {
    @Test
    fun `server mock delegates to its funcs and records calls`() {
        val mock =
            ServerEncoderDecoderMock(
                mustEncodeFunc = { _, _ -> "encoded".encodeToByteArray() },
                decodeBytesFunc = { _, _ -> Example("decoded") },
            )

        assertEquals("encoded", mock.mustEncode(serializer<Example>(), Example("x")).decodeToString())
        assertEquals(Example("decoded"), mock.decodeBytes<Example>("{}".encodeToByteArray(), serializer()))

        assertEquals(1, mock.mustEncodeCalls.size)
        assertEquals(Example("x"), mock.mustEncodeCalls.single())
        assertEquals(1, mock.decodeBytesCalls.size)
    }

    @Test
    fun `server mock records an encodeResponseWithStatus call`() {
        val res = RecordingResponseWriter()
        val mock = ServerEncoderDecoderMock(encodeResponseWithStatusFunc = { _, _, _, _ -> })

        mock.encodeResponseWithStatus(res, serializer<Example>(), Example("y"), HTTP_STATUS_OK)

        assertEquals(1, mock.encodeResponseWithStatusCalls.size)
        val call = mock.encodeResponseWithStatusCalls.single()
        assertEquals(Example("y"), call.value)
        assertEquals(HTTP_STATUS_OK, call.statusCode)
    }

    @Test
    fun `server mock throws when an unset func is called`() {
        val mock = ServerEncoderDecoderMock()
        assertFailsWith<IllegalStateException> { mock.mustEncodeJson(serializer<Example>(), Example("x")) }
    }

    @Test
    fun `client mock delegates to its funcs and records calls`() {
        val mock =
            ClientEncoderMock(
                contentTypeFunc = { "application/json" },
                unmarshalFunc = { _, _ -> Example("um") },
                encodeFunc = { out, _, _ -> out.write("e".encodeToByteArray()) },
                encodeReaderFunc = { _, _ -> ByteArrayInputStream("r".encodeToByteArray()) },
            )

        assertEquals("application/json", mock.contentType)
        assertEquals(Example("um"), mock.unmarshal<Example>("{}".encodeToByteArray(), serializer()))

        val out = ByteArrayOutputStream()
        mock.encode(out, serializer<Example>(), Example("z"))
        assertEquals("e", out.toByteArray().decodeToString())

        assertEquals("r", mock.encodeReader(serializer<Example>(), Example("z")).readBytes().decodeToString())

        assertEquals(1, mock.unmarshalCalls.size)
        assertEquals(1, mock.encodeCalls.size)
        assertEquals(Example("z"), mock.encodeCalls.single())
    }

    @Test
    fun `client mock throws when an unset func is called`() {
        val mock = ClientEncoderMock()
        assertFailsWith<IllegalStateException> { mock.encodeReader(serializer<Example>(), Example("x")) }
    }
}
