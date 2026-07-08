/*
 * Package compression provides data compression and decompression using the Zstd and S2
 * algorithms. Port of platform-go's `compression` package (see doc.go).
 *
 * A [Compressor] is a plain byte-in/byte-out codec selected by [Algorithm]; build one with
 * [newCompressor]. Decompression is bounded by a configurable cap ([DEFAULT_MAX_DECOMPRESSED_BYTES]
 * by default) so a small hostile payload cannot expand to exhaust memory — the decompression-bomb
 * guard Go implements with `WithDecoderMaxMemory` (zstd) and a manual `io.CopyN` cap (s2). Here a
 * single output-size cap is enforced uniformly across both backends.
 */
package com.primandproper.platform.compression

/**
 * Bounds how many bytes [Compressor.decompressBytes] will produce for a single input, guarding
 * against decompression bombs. Matches platform-go's `DefaultMaxDecompressedBytes` (64 MiB, zstd's
 * own default decoder memory limit). Override per-[Compressor] via [newCompressor].
 */
public const val DEFAULT_MAX_DECOMPRESSED_BYTES: Long = 64L shl 20 // 64 MiB

/**
 * Compresses and decompresses byte arrays. Port of platform-go's `Compressor` interface.
 */
public interface Compressor {
    /** Compresses [input], returning the compressed bytes. Mirrors Go's `CompressBytes`. */
    public fun compressBytes(input: ByteArray): ByteArray

    /**
     * Decompresses [input], returning the original bytes. Throws [DecompressedTooLargeException]
     * if the output would exceed the configured cap, and an [java.io.IOException] on malformed
     * input. Mirrors Go's `DecompressBytes`.
     */
    public fun decompressBytes(input: ByteArray): ByteArray
}

/**
 * Builds a [Compressor] for the given [algorithm], capping decompressed output at
 * [maxDecompressedBytes]. A value `<= 0` leaves the default ([DEFAULT_MAX_DECOMPRESSED_BYTES]) in
 * place, mirroring Go's `WithMaxDecompressedBytes` `n > 0` guard.
 *
 * Unlike Go — where a stringly-typed `Algorithm` can be unknown and `NewCompressor` returns
 * `ErrInvalidAlgorithm` — the [Algorithm] enum here is exhaustive, so this overload never fails on
 * a bad codec. Resolve an untrusted config string through [Algorithm.fromValue] or the
 * [CompressionConfig] overload, which raises [InvalidAlgorithmException] on an unknown name.
 */
public fun newCompressor(
    algorithm: Algorithm,
    maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
): Compressor {
    val cap = if (maxDecompressedBytes > 0) maxDecompressedBytes else DEFAULT_MAX_DECOMPRESSED_BYTES
    return when (algorithm) {
        Algorithm.ZSTD -> ZstdCompressor(cap)
        Algorithm.S2 -> S2Compressor(cap)
    }
}

/**
 * Builds a [Compressor] from [config], resolving [CompressionConfig.algorithm] via
 * [Algorithm.fromValue]. Throws [InvalidAlgorithmException] when the string names no known
 * algorithm — the analog of Go's `NewCompressor` returning `ErrInvalidAlgorithm`.
 */
public fun newCompressor(config: CompressionConfig): Compressor {
    val algorithm = Algorithm.fromValue(config.algorithm) ?: throw InvalidAlgorithmException(config.algorithm)
    return newCompressor(algorithm, config.maxDecompressedBytes)
}
