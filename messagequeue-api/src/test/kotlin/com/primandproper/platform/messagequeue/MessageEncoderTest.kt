package com.primandproper.platform.messagequeue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageEncoderTest {
    @Test
    fun `RawMessageEncoder passes a ByteArray through verbatim`() {
        val bytes = byteArrayOf(1, 2, 3)
        assertSame(bytes, RawMessageEncoder.encode(bytes))
    }

    @Test
    fun `RawMessageEncoder UTF-8 encodes a String`() {
        assertTrue("héllo".encodeToByteArray().contentEquals(RawMessageEncoder.encode("héllo")))
    }

    @Test
    fun `RawMessageEncoder rejects a structured value`() {
        assertFailsWith<IllegalArgumentException> { RawMessageEncoder.encode(42) }
    }

    @Test
    fun `a functional MessageEncoder can be supplied`() {
        val encoder = MessageEncoder { data -> "<$data>".encodeToByteArray() }
        assertEquals("<x>", encoder.encode("x").decodeToString())
    }
}
