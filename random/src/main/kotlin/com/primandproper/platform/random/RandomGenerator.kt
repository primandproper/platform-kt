package com.primandproper.platform.random

/**
 * Generates cryptographically secure random values in a handful of encodings. Port of platform-go's
 * `random.Generator`.
 *
 * Go's methods return `(value, error)` because `crypto/rand` reads can fail (the source runs dry or
 * an injected reader errors). Kotlin has no analogous split return; implementations throw instead —
 * see [SecureRandomGenerator], whose production [RandomSource] (`java.security.SecureRandom`) for all
 * practical purposes never fails, and whose test-only [RandomSource] seam exists precisely so a test
 * can force the failure path deterministically.
 *
 * [com.primandproper.platform.random.noop.NoopRandomGenerator] and
 * [com.primandproper.platform.random.mock.RandomGeneratorMock] are test doubles, mirroring Go's
 * `random/noop` and `random/mock` packages respectively.
 */
public interface RandomGenerator {
    /** Returns [length] cryptographically secure random bytes. */
    public fun generateRawBytes(length: Int): ByteArray

    /** Returns [length] random bytes, lower-case hex-encoded (so the string is `2 * length` chars). */
    public fun generateHexEncodedString(length: Int): String

    /** Returns [length] random bytes, Base32-encoded (RFC 4648 standard alphabet, `=`-padded). */
    public fun generateBase32EncodedString(length: Int): String

    /** Returns [length] random bytes, Base64url-encoded without padding (Go's `RawURLEncoding`). */
    public fun generateBase64EncodedString(length: Int): String

    /**
     * Returns a string of [length] characters drawn uniformly from [alphabet] (e.g. digits-only OTP
     * codes, or a specific charset a downstream token format requires), using rejection sampling so
     * the distribution stays uniform even when `alphabet.length` doesn't evenly divide 256.
     *
     * Not present in the Go source (`random.go` only offers the three fixed encodings above plus raw
     * bytes) — added here so a caller needing a custom character set doesn't have to post-process an
     * encoded string and reason about which characters survived.
     */
    public fun generateAlphabetEncodedString(
        alphabet: String,
        length: Int,
    ): String
}

/**
 * Shared rejection-sampling core for [RandomGenerator.generateAlphabetEncodedString] implementations.
 * Pulls single bytes from [nextByte] until each one falls within the largest multiple of
 * `alphabet.length` that fits in a byte (`[0, limit)`), discarding the rest — the standard technique
 * for mapping a uniform byte onto a uniform index over an arbitrary-size alphabet without modulo
 * bias.
 */
internal fun buildAlphabetString(
    alphabet: String,
    length: Int,
    nextByte: () -> Int,
): String {
    require(alphabet.isNotEmpty()) { "alphabet must not be empty" }
    require(length >= 0) { "length must not be negative, was $length" }

    val bound = alphabet.length
    val limit = 256 - (256 % bound)
    val sb = StringBuilder(length)
    while (sb.length < length) {
        val b = nextByte() and 0xFF
        if (b < limit) sb.append(alphabet[b % bound])
    }
    return sb.toString()
}
