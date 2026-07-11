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
 * The low [byteCount] bytes of [value] as a big-endian [ByteArray], mirroring how Go's
 * `hash.Hash.Sum` writes a fixed-width checksum (so the result is always [byteCount] bytes,
 * zero-padded on the high end). Hex-encoding the result yields `byteCount * 2` characters.
 */
internal fun uLongToBytes(
    value: Long,
    byteCount: Int,
): ByteArray {
    val out = ByteArray(byteCount)
    for (i in 0 until byteCount) {
        out[i] = (value ushr ((byteCount - 1 - i) * 8)).toByte()
    }
    return out
}
