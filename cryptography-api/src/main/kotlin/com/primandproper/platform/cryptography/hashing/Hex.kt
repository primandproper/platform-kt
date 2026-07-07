package com.primandproper.platform.cryptography.hashing

private const val HEX_DIGITS = "0123456789abcdef"

/** Lowercase hex-encodes [this], mirroring Go's `encoding/hex.EncodeToString`. */
internal fun ByteArray.toHexLower(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_DIGITS[v ushr 4])
        out.append(HEX_DIGITS[v and 0x0F])
    }
    return out.toString()
}

/**
 * Lowercase hex-encodes the big-endian bytes of the low [byteCount] bytes of [value], mirroring how
 * Go's `hash.Hash.Sum` writes a fixed-width checksum before hex-encoding (so the result is always
 * `byteCount * 2` characters, zero-padded).
 */
internal fun uLongToHexLower(
    value: Long,
    byteCount: Int,
): String {
    val out = StringBuilder(byteCount * 2)
    for (i in byteCount - 1 downTo 0) {
        val b = (value ushr (i * 8)).toInt() and 0xFF
        out.append(HEX_DIGITS[b ushr 4])
        out.append(HEX_DIGITS[b and 0x0F])
    }
    return out.toString()
}
