package com.primandproper.platform.encoding

import com.primandproper.platform.observability.testing.RecordingObserver
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `client_encoder_test.go`. */
class ClientEncoderTest {
    private fun recording(contentType: ContentType): Pair<ClientEncoder, RecordingObserver> {
        val obs = RecordingObserver()
        return DefaultClientEncoder(contentType, obs) to obs
    }

    @Test
    fun `ProvideClientEncoder yields a non-null encoder`() {
        val e = ClientEncoder(ContentType.JSON)
        assertTrue(e.contentType.isNotEmpty())
    }

    @Test
    fun `unmarshal round-trips JSON`() {
        val e = ClientEncoder(ContentType.JSON)
        assertEquals(Example("name"), e.unmarshal("""{"name":"name"}""".encodeToByteArray(), Example.serializer()))
    }

    @Test
    fun `unmarshal observes the data length`() {
        val (e, obs) = recording(ContentType.JSON)
        val data = """{"name":"name"}""".encodeToByteArray()
        e.unmarshal(data, Example.serializer())

        obs.assertObservedOperationWithValues("data_length" to data.size)
    }

    @Test
    fun `unmarshal observes the data length even on failure`() {
        val (e, obs) = recording(ContentType.JSON)
        val data = """{"name"   """.encodeToByteArray()

        assertFailsWith<Exception> { e.unmarshal(data, Example.serializer()) }

        val op = obs.operations.first { it.name == "Unmarshal" }
        assertEquals(data.size, op.values["data_length"])
    }

    @Test
    fun `unmarshal fails on invalid data`() {
        val e = ClientEncoder(ContentType.JSON)
        assertFailsWith<Exception> { e.unmarshal("""{"name"   """.encodeToByteArray(), Example.serializer()) }
    }

    @Test
    fun `encode writes JSON to the destination`() {
        val e = ClientEncoder(ContentType.JSON)
        val out = ByteArrayOutputStream()
        e.encode(out, Example.serializer(), Example("name"))
        assertEquals("""{"name":"name"}""", out.toByteArray().decodeToString())
    }

    @Test
    fun `encode surfaces destination write errors`() {
        val e = ClientEncoder(ContentType.JSON)
        assertFailsWith<Exception> { e.encode(ThrowingOutputStream(), Example.serializer(), Example("name")) }
    }

    @Test
    fun `encode fails on a broken payload`() {
        val e = ClientEncoder(ContentType.JSON)
        assertFailsWith<Exception> { e.encode(ByteArrayOutputStream(), BrokenSerializer, Example("boom")) }
    }

    @Test
    fun `encodeReader returns a reader over the encoded bytes and observes length`() {
        val (e, obs) = recording(ContentType.JSON)
        val reader = e.encodeReader(Example.serializer(), Example("name"))

        assertEquals("""{"name":"name"}""", reader.readBytes().decodeToString())
        val op = obs.operations.first { it.name == "EncodeReader" }
        assertEquals(15, op.values["length"])
    }

    @Test
    fun `encodeReader fails on a broken payload`() {
        val e = ClientEncoder(ContentType.JSON)
        assertFailsWith<Exception> { e.encodeReader(BrokenSerializer, Example("boom")) }
    }

    @Test
    fun `contentType reports the configured media type`() {
        assertEquals("application/json", ClientEncoder(ContentType.JSON).contentType)
        assertEquals("application/xml", ClientEncoder(ContentType.XML).contentType)
    }

    @Test
    fun `reified conveniences derive the serializer`() {
        val e = ClientEncoder(ContentType.JSON)
        val out = ByteArrayOutputStream()
        e.encode(out, Example("reified"))
        assertEquals(Example("reified"), e.unmarshal<Example>(out.toByteArray()))
    }

    @Test
    fun `non-JSON formats are documented TODO seams`() {
        for (ct in listOf(ContentType.XML, ContentType.TOML, ContentType.YAML, ContentType.EMOJI)) {
            val e = ClientEncoder(ct)
            assertFailsWith<UnsupportedContentFormatException> {
                e.encode(ByteArrayOutputStream(), Example.serializer(), Example("x"))
            }
            assertFailsWith<UnsupportedContentFormatException> {
                e.unmarshal("x".encodeToByteArray(), Example.serializer())
            }
        }
    }
}
