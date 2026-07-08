package com.primandproper.platform.compression

import org.xerial.snappy.SnappyFramedInputStream
import org.xerial.snappy.SnappyFramedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * S2 backend, the port of platform-go's `AlgorithmS2` branch (klauspost/compress/s2).
 *
 * DIVERGENCE (S2 → Snappy): platform-kt has no pure-JVM S2 implementation, so this backend uses
 * snappy-java's *framed* streams — the closest available analog. S2 is a Snappy-derived format and
 * snappy-java's `SnappyFramedOutputStream`/`SnappyFramedInputStream` implement the standard Snappy
 * stream framing (magic `sNaPpY`) that S2 extends. They are, however, NOT byte-compatible with Go's
 * s2 on the wire: klauspost/s2 emits its own `S2sTwO` chunk framing and extension chunk types.
 * Consequently a payload compressed here round-trips correctly within the JVM but does NOT
 * interoperate with a Go s2 reader (or vice versa). If exact S2 framing / cross-language interop
 * becomes a requirement, replace this backend with a real S2 codec — a documented TODO(s2) seam.
 *
 * Decompression is bounded by [maxDecompressedBytes] via [readCapped]; Go bounds s2 with a manual
 * `io.CopyN(max+1)` since its s2 reader has no built-in output cap.
 */
internal class S2Compressor(
    private val maxDecompressedBytes: Long,
) : Compressor {
    override fun compressBytes(input: ByteArray): ByteArray {
        val sink = ByteArrayOutputStream()
        SnappyFramedOutputStream(sink).use { it.write(input) }
        return sink.toByteArray()
    }

    override fun decompressBytes(input: ByteArray): ByteArray =
        SnappyFramedInputStream(ByteArrayInputStream(input)).use { it.readCapped(maxDecompressedBytes) }
}
