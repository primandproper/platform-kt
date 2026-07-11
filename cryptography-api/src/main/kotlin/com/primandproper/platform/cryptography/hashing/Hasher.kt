package com.primandproper.platform.cryptography.hashing

/**
 * Hashes a string into a lowercase hex-encoded digest. Port of platform-go's `hashing.Hasher`.
 *
 * NOTE: implementations of this interface vary in cryptographic strength. The sha256 and sha512
 * implementations are cryptographic hashes; the adler32, crc64, and fnv implementations are
 * NON-CRYPTOGRAPHIC checksums and MUST NOT be selected for security, password, or tamper-resistance
 * purposes. Choose the implementation deliberately.
 */
public fun interface Hasher {
    /** Returns the raw digest bytes of [content]. */
    public fun hash(content: ByteArray): ByteArray

    /** UTF-8-encodes [content], hashes it, and returns the lowercase hex-encoded digest. */
    public fun hash(content: String): String = hash(content.toByteArray(Charsets.UTF_8)).toHexLower()
}
