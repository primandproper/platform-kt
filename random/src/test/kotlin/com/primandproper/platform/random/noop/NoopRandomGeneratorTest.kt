package com.primandproper.platform.random.noop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoopRandomGeneratorTest {
    @Test
    fun `generateHexEncodedString returns an empty string`() {
        assertEquals("", NoopRandomGenerator.generateHexEncodedString(32))
    }

    @Test
    fun `generateBase32EncodedString returns an empty string`() {
        assertEquals("", NoopRandomGenerator.generateBase32EncodedString(32))
    }

    @Test
    fun `generateBase64EncodedString returns an empty string`() {
        assertEquals("", NoopRandomGenerator.generateBase64EncodedString(32))
    }

    @Test
    fun `generateAlphabetEncodedString returns an empty string`() {
        assertEquals("", NoopRandomGenerator.generateAlphabetEncodedString("abc", 32))
    }

    @Test
    fun `generateRawBytes returns an empty, non-null byte array`() {
        val value = NoopRandomGenerator.generateRawBytes(32)

        assertTrue(value.isEmpty())
    }

    @Test
    fun `is deterministic across repeated calls`() {
        assertEquals(
            NoopRandomGenerator.generateHexEncodedString(16),
            NoopRandomGenerator.generateHexEncodedString(16),
        )
    }
}
