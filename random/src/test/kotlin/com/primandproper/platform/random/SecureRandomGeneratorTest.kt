package com.primandproper.platform.random

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val EXAMPLE_LENGTH = 32

class SecureRandomGeneratorTest {
    private fun newRecordingGenerator(): Pair<SecureRandomGenerator, RecordingObserver> {
        val obs = RecordingObserver()
        return SecureRandomGenerator(obs, SecureRandomSource) to obs
    }

    // -- generateRawBytes ----------------------------------------------------------------------

    @Test
    fun `generateRawBytes returns exactly the requested number of bytes`() {
        val (generator, _) = newRecordingGenerator()

        val value = generator.generateRawBytes(EXAMPLE_LENGTH)

        assertEquals(EXAMPLE_LENGTH, value.size)
    }

    @Test
    fun `generateRawBytes records the requested length on the observer`() {
        val (generator, obs) = newRecordingGenerator()

        generator.generateRawBytes(EXAMPLE_LENGTH)

        obs.assertObservedOperationWithValues(Keys.LENGTH to EXAMPLE_LENGTH)
    }

    @Test
    fun `generateRawBytes two calls are not identical (sanity check on entropy)`() {
        val (generator, _) = newRecordingGenerator()

        val a = generator.generateRawBytes(EXAMPLE_LENGTH)
        val b = generator.generateRawBytes(EXAMPLE_LENGTH)

        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `generateRawBytes propagates and records a failing source`() {
        val obs = RecordingObserver()
        val generator = SecureRandomGenerator(obs, RandomSource { error("boom") })

        assertFailsWith<IllegalStateException> { generator.generateRawBytes(EXAMPLE_LENGTH) }

        val op = obs.operations.single()
        assertEquals(1, op.errors.size)
        assertTrue(op.ended)
    }

    // -- generateHexEncodedString ---------------------------------------------------------------

    @Test
    fun `generateHexEncodedString returns lowercase hex of twice the byte length`() {
        val (generator, _) = newRecordingGenerator()

        val value = generator.generateHexEncodedString(EXAMPLE_LENGTH)

        assertEquals(EXAMPLE_LENGTH * 2, value.length)
        assertTrue(value.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `generateHexEncodedString records the requested length and rethrows on failure`() {
        val obs = RecordingObserver()
        val generator = SecureRandomGenerator(obs, RandomSource { error("boom") })

        assertFailsWith<IllegalStateException> { generator.generateHexEncodedString(EXAMPLE_LENGTH) }

        obs.assertObservedOperationWithValues(Keys.LENGTH to EXAMPLE_LENGTH)
        assertEquals(1, obs.operations.single().errors.size)
    }

    // -- generateBase32EncodedString ------------------------------------------------------------

    @Test
    fun `generateBase32EncodedString is a padded multiple of 8 chars using the standard alphabet`() {
        val (generator, _) = newRecordingGenerator()

        val value = generator.generateBase32EncodedString(EXAMPLE_LENGTH)

        assertEquals(0, value.length % 8)
        assertTrue(value.matches(Regex("[A-Z2-7=]+")))
    }

    @Test
    fun `generateBase32EncodedString matches the known RFC 4648 test vector`() {
        val (generator, _) = newRecordingGenerator()

        assertEquals("MZXW6YTBOI======", base32Encode("foobar".toByteArray()))
        // sanity: the generator round-trips through the same encoder for real random input.
        assertTrue(generator.generateBase32EncodedString(6).isNotEmpty())
    }

    // -- generateBase64EncodedString ------------------------------------------------------------

    @Test
    fun `generateBase64EncodedString is url-safe and unpadded`() {
        val (generator, _) = newRecordingGenerator()

        val value = generator.generateBase64EncodedString(EXAMPLE_LENGTH)

        assertTrue(value.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(!value.contains("="))
    }

    // -- generateAlphabetEncodedString ----------------------------------------------------------

    @Test
    fun `generateAlphabetEncodedString draws only from the given alphabet`() {
        val (generator, _) = newRecordingGenerator()
        val alphabet = "0123456789"

        val value = generator.generateAlphabetEncodedString(alphabet, 24)

        assertEquals(24, value.length)
        value.forEach { assertTrue(alphabet.contains(it), "'$it' not in alphabet") }
    }

    @Test
    fun `generateAlphabetEncodedString rejects an empty alphabet`() {
        val (generator, _) = newRecordingGenerator()

        assertFailsWith<IllegalArgumentException> { generator.generateAlphabetEncodedString("", 8) }
    }

    // -- top-level convenience functions ---------------------------------------------------------

    @Test
    fun `top-level generateHexEncodedString delegates to the default generator`() {
        val value = generateHexEncodedString(16)

        assertEquals(32, value.length)
    }

    @Test
    fun `top-level generateRawBytes returns the requested length`() {
        val value = generateRawBytes(8)

        assertEquals(8, value.size)
    }
}
