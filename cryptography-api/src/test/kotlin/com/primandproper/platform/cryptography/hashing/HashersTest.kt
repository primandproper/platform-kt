package com.primandproper.platform.cryptography.hashing

import com.primandproper.platform.cryptography.hashing.adler32.newAdler32Hasher
import com.primandproper.platform.cryptography.hashing.crc64.newCRC64Hasher
import com.primandproper.platform.cryptography.hashing.fnv.newFNVHasher
import com.primandproper.platform.cryptography.hashing.sha256.newSHA256Hasher
import com.primandproper.platform.cryptography.hashing.sha512.newSHA512Hasher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-answer tests. Each expected digest is the value produced by the corresponding platform-go
 * hasher for the same input (the Go tests hash `t.Name()`, e.g. "Test_sha256Hasher_Hash/standard"),
 * proving the Kotlin ports match Go byte-for-byte.
 */
class HashersTest {
    @Test
    fun sha256MatchesGo() {
        assertEquals(
            "f469799cfc8eb5c3fa03e2ec4faf3c1b9a4c3a1c0ac3557a2f963e598cea695f",
            newSHA256Hasher().hash("Test_sha256Hasher_Hash/standard"),
        )
    }

    @Test
    fun sha512EmptyStringIsCanonical() {
        // The canonical SHA-512 of the empty string; guards the JDK wiring for the 512 variant.
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            newSHA512Hasher().hash(""),
        )
    }

    @Test
    fun crc64MatchesGo() {
        assertEquals("cee81309a5f73f5c", newCRC64Hasher().hash("Test_crc64Hasher_Hash/standard"))
    }

    @Test
    fun fnvMatchesGo() {
        assertEquals("780242af2cb9fb3c85ad54840e9411ec", newFNVHasher().hash("Test_fnvHasher_Hash/standard"))
    }

    @Test
    fun adler32MatchesGo() {
        assertEquals("c7060c2b", newAdler32Hasher().hash("Test_adler32Hasher_Hash/standard"))
    }

    @Test
    fun crc64OfEmptyStringIsZeroWidthPadded() {
        // CRC-64 of the empty input is 0, and Go emits a fixed 16-char zero-padded hex string.
        assertEquals("0000000000000000", newCRC64Hasher().hash(""))
    }

    @Test
    fun adler32OfEmptyStringIsOne() {
        // Adler-32 seed is 1; Go emits it as 8 zero-padded hex chars.
        assertEquals("00000001", newAdler32Hasher().hash(""))
    }
}
