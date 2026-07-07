package com.primandproper.platform.compression

/**
 * Portable compression configuration. Bundles the chosen [algorithm] (as its wire/config string,
 * mirroring how Go carries the algorithm as a plain config string) with the [maxDecompressedBytes]
 * bomb-guard cap that Go threads in via `WithMaxDecompressedBytes`.
 *
 * Pass an instance to [newCompressor] to resolve the algorithm and build a [Compressor]; an
 * unknown [algorithm] string raises [InvalidAlgorithmException], matching Go's `NewCompressor`
 * returning `ErrInvalidAlgorithm`.
 *
 * @param algorithm the algorithm's string form (e.g. `"zstd"` or `"s2"`), resolved via
 *   [Algorithm.fromValue].
 * @param maxDecompressedBytes the decompression cap; a value `<= 0` leaves the default
 *   ([DEFAULT_MAX_DECOMPRESSED_BYTES]) in place, matching Go's `WithMaxDecompressedBytes` `n > 0`
 *   guard.
 */
public data class CompressionConfig(
    val algorithm: String,
    val maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
)
