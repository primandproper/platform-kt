package com.primandproper.platform.random.noop

import com.primandproper.platform.random.RandomGenerator

/**
 * A no-op [RandomGenerator]: every method returns an empty value immediately and touches no CSPRNG.
 * Port of platform-go's `random/noop.Generator` — a safe default for wiring, and for tests that don't
 * care about the actual random output.
 */
public object NoopRandomGenerator : RandomGenerator {
    override fun generateRawBytes(length: Int): ByteArray = ByteArray(0)

    override fun generateHexEncodedString(length: Int): String = ""

    override fun generateBase32EncodedString(length: Int): String = ""

    override fun generateBase64EncodedString(length: Int): String = ""

    override fun generateAlphabetEncodedString(
        alphabet: String,
        length: Int,
    ): String = ""
}
