package com.primandproper.platform.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `do_test.go` (the package-level `Decode`/`MustEncode`/… helpers). */
class EncodingTest {
    @Test
    fun `decode with a null content type defaults to JSON`() {
        assertEquals(Example("test"), decode("""{"name":"test"}""".encodeToByteArray(), null, Example.serializer()))
    }

    @Test
    fun `decode with an explicit JSON content type`() {
        val out = decode("""{"name":"test"}""".encodeToByteArray(), ContentType.JSON, Example.serializer())
        assertEquals(Example("test"), out)
    }

    @Test
    fun `decode fails on invalid data`() {
        assertFailsWith<Exception> { decode("""{invalid""".encodeToByteArray(), null, Example.serializer()) }
    }

    @Test
    fun `encode with a null content type produces bytes`() {
        val result = encode(Example("must"), null, Example.serializer())
        assertTrue(result.isNotEmpty())
        assertEquals("""{"name":"must"}""", result.decodeToString())
    }

    @Test
    fun `encode throws on a broken payload`() {
        assertFailsWith<Exception> { encode(Example("boom"), null, BrokenSerializer) }
    }

    @Test
    fun `encodeJson produces JSON bytes`() {
        assertEquals("""{"name":"j"}""", encodeJson(Example("j"), Example.serializer()).decodeToString())
    }

    @Test
    fun `decodeJson round-trips`() {
        assertEquals(Example("test"), decodeJson("""{"name":"test"}""".encodeToByteArray(), Example.serializer()))
    }

    @Test
    fun `decodeJson fails on invalid data`() {
        assertFailsWith<Exception> { decodeJson("""{invalid""".encodeToByteArray(), Example.serializer()) }
    }

    @Test
    fun `jsonIntoReader yields a readable stream`() {
        val reader = jsonIntoReader(Example("reader"), Example.serializer())
        assertEquals("""{"name":"reader"}""", reader.readBytes().decodeToString())
    }

    @Test
    fun `reified helpers derive the serializer`() {
        val bytes = encode(Example("reified"))
        assertEquals(Example("reified"), decode<Example>(bytes))
        assertEquals(Example("reified"), decodeJson<Example>(encodeJson(Example("reified"))))
    }
}
