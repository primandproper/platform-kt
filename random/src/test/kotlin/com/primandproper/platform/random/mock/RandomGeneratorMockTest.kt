package com.primandproper.platform.random.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RandomGeneratorMockTest {
    @Test
    fun `delegates to the configured func and records the call`() {
        val mock = RandomGeneratorMock(generateHexEncodedStringFunc = { length -> "a".repeat(length) })

        val value = mock.generateHexEncodedString(4)

        assertEquals("aaaa", value)
        assertEquals(listOf(4), mock.generateHexEncodedStringCalls)
    }

    @Test
    fun `calling a method with no func configured throws`() {
        val mock = RandomGeneratorMock()

        assertFailsWith<IllegalStateException> { mock.generateRawBytes(8) }
    }

    @Test
    fun `generateAlphabetEncodedString records both arguments`() {
        val mock = RandomGeneratorMock(generateAlphabetEncodedStringFunc = { alphabet, length -> alphabet.take(length) })

        mock.generateAlphabetEncodedString("0123456789", 3)

        assertEquals(listOf("0123456789" to 3), mock.generateAlphabetEncodedStringCalls)
    }
}
