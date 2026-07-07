package com.primandproper.platform.compression

import com.primandproper.platform.errors.PlatformException

/*
 * The compression sentinels. platform-go declares these as `var Err… = errors.New(…)` values that
 * callers match with `errors.Is`; here they are dedicated [PlatformException] subtypes callers match
 * with a plain `catch`/`is`, exactly as the cryptography port maps its Go sentinels onto exceptions.
 */

/**
 * Thrown when an unsupported or empty compression algorithm is requested. Mirrors Go's
 * `ErrInvalidAlgorithm`, returned by `NewCompressor` for an unknown [Algorithm].
 */
public class InvalidAlgorithmException(
    algorithm: String,
) : PlatformException("invalid compression algorithm: \"$algorithm\"")

/**
 * Thrown when decompressing an input would exceed the configured maximum decompressed size — the
 * decompression-bomb guard. Mirrors Go's `ErrDecompressedTooLarge`.
 */
public class DecompressedTooLargeException :
    PlatformException("decompressed output exceeds configured maximum")
