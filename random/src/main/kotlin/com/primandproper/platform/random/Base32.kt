package com.primandproper.platform.random

// RFC 4648 standard alphabet — the same one Go's encoding/base32.StdEncoding uses.
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/**
 * RFC 4648 Base32 encoding with standard padding, matching Go's `base32.StdEncoding.EncodeToString`.
 * The JVM standard library has no Base32 codec (unlike Base64), so this hand-rolls the same bit
 * accumulator technique `java.util.Base64` and Go's own encoder use: buffer bytes into a sliding
 * window, drain 5 bits at a time into an alphabet index, and pad the final group with `=` up to a
 * multiple of 8 output characters.
 */
internal fun base32Encode(data: ByteArray): String {
    if (data.isEmpty()) return ""

    val sb = StringBuilder(((data.size + 4) / 5) * 8)
    var buffer = 0L
    var bitsInBuffer = 0

    for (b in data) {
        buffer = (buffer shl 8) or (b.toLong() and 0xFF)
        bitsInBuffer += 8
        while (bitsInBuffer >= 5) {
            bitsInBuffer -= 5
            val index = ((buffer shr bitsInBuffer) and 0x1F).toInt()
            sb.append(BASE32_ALPHABET[index])
        }
    }

    if (bitsInBuffer > 0) {
        val index = ((buffer shl (5 - bitsInBuffer)) and 0x1F).toInt()
        sb.append(BASE32_ALPHABET[index])
    }

    while (sb.length % 8 != 0) {
        sb.append('=')
    }

    return sb.toString()
}
