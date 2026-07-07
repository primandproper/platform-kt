package com.primandproper.platform.compression

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Mirrors platform-go's `compression/compressor_test.go`. */
class CompressorTest {
    private val algorithms = listOf(Algorithm.ZSTD, Algorithm.S2)

    @Test
    fun roundTripsSampleInputForEachCodec() {
        val sample = """{"name":"testing"}""".toByteArray()
        for (algorithm in algorithms) {
            val compressor = newCompressor(algorithm)
            val compressed = compressor.compressBytes(sample)
            val decompressed = compressor.decompressBytes(compressed)
            assertContentEquals(sample, decompressed, "round-trip mismatch for $algorithm")
        }
    }

    @Test
    fun roundTripsEmptyInputForEachCodec() {
        val empty = ByteArray(0)
        for (algorithm in algorithms) {
            val compressor = newCompressor(algorithm)
            val decompressed = compressor.decompressBytes(compressor.compressBytes(empty))
            assertContentEquals(empty, decompressed, "empty round-trip mismatch for $algorithm")
        }
    }

    @Test
    fun roundTripsLargeRepetitiveInputAndShrinksIt() {
        // Highly compressible: the same phrase repeated many times.
        val large = "the quick brown fox ".repeat(4096).toByteArray()
        for (algorithm in algorithms) {
            val compressor = newCompressor(algorithm)
            val compressed = compressor.compressBytes(large)
            assertTrue(
                compressed.size < large.size,
                "expected compressed ($algorithm) ${compressed.size} < original ${large.size}",
            )
            val decompressed = compressor.decompressBytes(compressed)
            assertContentEquals(large, decompressed, "large round-trip mismatch for $algorithm")
        }
    }

    @Test
    fun selectsCodecFromConfigString() {
        for (algorithm in algorithms) {
            val compressor = newCompressor(CompressionConfig(algorithm.value))
            val payload = "codec selection via config".toByteArray()
            val decompressed = compressor.decompressBytes(compressor.compressBytes(payload))
            assertContentEquals(payload, decompressed, "config-selected round-trip mismatch for $algorithm")
        }
    }

    @Test
    fun rejectsUnknownConfigAlgorithm() {
        assertFailsWith<InvalidAlgorithmException> { newCompressor(CompressionConfig("not-a-codec")) }
    }

    @Test
    fun rejectsMalformedInputForEachCodec() {
        for (algorithm in algorithms) {
            val compressor = newCompressor(algorithm)
            assertFailsWith<IOException>("expected malformed input to fail for $algorithm") {
                compressor.decompressBytes("definitely not valid compressed data".toByteArray())
            }
        }
    }

    @Test
    fun rejectsOutputLargerThanCap() {
        // 1 MiB of zeros compresses to a tiny payload but expands well past a 4 KiB cap.
        val maxOut = 4L shl 10
        val bomb = ByteArray(1 shl 20)
        for (algorithm in algorithms) {
            val packer = newCompressor(algorithm)
            val compressed = packer.compressBytes(bomb)
            // Sanity: the bomb compresses well (far below its 1 MiB decompressed size). The exact
            // ratio varies by codec — zstd shrinks zeros to a few dozen bytes, snappy-framed carries
            // more per-block/CRC overhead — so check "compresses a lot", not "under the 4 KiB cap".
            assertTrue(compressed.size < bomb.size / 8, "bomb payload ($algorithm) should compress well")

            val capped = newCompressor(algorithm, maxDecompressedBytes = maxOut)
            assertFailsWith<DecompressedTooLargeException>("cap not enforced for $algorithm") {
                capped.decompressBytes(compressed)
            }
        }
    }

    @Test
    fun allowsOutputWithinCap() {
        val payload = "a modest payload well under the configured cap".toByteArray()
        for (algorithm in algorithms) {
            val packer = newCompressor(algorithm)
            val compressed = packer.compressBytes(payload)

            val capped = newCompressor(algorithm, maxDecompressedBytes = 1L shl 20)
            assertContentEquals(payload, capped.decompressBytes(compressed), "within-cap mismatch for $algorithm")
        }
    }

    @Test
    fun nonPositiveCapFallsBackToDefault() {
        // A cap of 0 leaves DEFAULT_MAX_DECOMPRESSED_BYTES in place, mirroring Go's `n > 0` guard,
        // so an ordinary payload still decompresses.
        val payload = "cap fallback".toByteArray()
        for (algorithm in algorithms) {
            val compressor = newCompressor(algorithm, maxDecompressedBytes = 0)
            assertContentEquals(payload, compressor.decompressBytes(compressor.compressBytes(payload)))
        }
    }
}
