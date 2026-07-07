package com.primandproper.platform.compression

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Zstandard backend, the port of platform-go's `AlgorithmZstd` branch (klauspost/compress/zstd).
 * Uses zstd-jni's streaming writer/reader at the library's default compression level, mirroring
 * Go's `zstd.NewWriter`/`zstd.NewReader` with default options.
 *
 * Decompression is bounded by [maxDecompressedBytes] via [readCapped]; Go achieves the same with
 * `zstd.WithDecoderMaxMemory`.
 */
internal class ZstdCompressor(
    private val maxDecompressedBytes: Long,
) : Compressor {
    override fun compressBytes(input: ByteArray): ByteArray {
        val sink = ByteArrayOutputStream()
        ZstdOutputStream(sink).use { it.write(input) }
        return sink.toByteArray()
    }

    override fun decompressBytes(input: ByteArray): ByteArray =
        ZstdInputStream(ByteArrayInputStream(input)).use { it.readCapped(maxDecompressedBytes) }
}
