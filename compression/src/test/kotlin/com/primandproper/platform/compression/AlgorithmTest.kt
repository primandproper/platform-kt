package com.primandproper.platform.compression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Covers [Algorithm.fromValue], the analog of Go converting a config string to an `Algorithm`. */
class AlgorithmTest {
    @Test
    fun resolvesKnownValues() {
        assertEquals(Algorithm.ZSTD, Algorithm.fromValue("zstd"))
        assertEquals(Algorithm.S2, Algorithm.fromValue("s2"))
    }

    @Test
    fun normalizesWhitespaceAndCase() {
        assertEquals(Algorithm.ZSTD, Algorithm.fromValue("  ZSTD  "))
        assertEquals(Algorithm.S2, Algorithm.fromValue("S2"))
    }

    @Test
    fun returnsNullForUnknownValue() {
        assertNull(Algorithm.fromValue("brotli"))
        assertNull(Algorithm.fromValue(""))
    }
}
