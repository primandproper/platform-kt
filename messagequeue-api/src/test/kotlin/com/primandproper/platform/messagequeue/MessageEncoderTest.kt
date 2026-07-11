package com.primandproper.platform.messagequeue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageEncoderTest {
    @Test
    fun `ByteArrayMessageEncoder passes a ByteArray through verbatim`() {
        val bytes = byteArrayOf(1, 2, 3)
        assertSame(bytes, ByteArrayMessageEncoder.encode(bytes))
    }

    @Test
    fun `StringMessageEncoder UTF-8 encodes a String`() {
        assertTrue("héllo".encodeToByteArray().contentEquals(StringMessageEncoder.encode("héllo")))
    }

    @Test
    fun `a functional MessageEncoder can be supplied`() {
        val encoder = MessageEncoder<Int> { data -> "<$data>".encodeToByteArray() }
        assertEquals("<42>", encoder.encode(42).decodeToString())
    }
}
