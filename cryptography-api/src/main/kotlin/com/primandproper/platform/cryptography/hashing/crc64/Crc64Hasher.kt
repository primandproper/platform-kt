package com.primandproper.platform.cryptography.hashing.crc64

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.uLongToHexLower

/**
 * Returns a [Hasher] backed by the CRC-64 (ISO) checksum, matching platform-go's
 * `crc64.NewCRC64Hasher` (which uses `crc64.MakeTable(crc64.ISO)`) byte-for-byte.
 *
 * WARNING: CRC-64 is a NON-CRYPTOGRAPHIC checksum and MUST NOT be used for security, password, or
 * tamper-resistance purposes.
 */
public fun newCRC64Hasher(): Hasher = Crc64Hasher

private object Crc64Hasher : Hasher {
    // crc64.ISO polynomial, reflected form, exactly as Go's hash/crc64 uses it.
    private const val POLY = -0x2800000000000000L // 0xD800000000000000

    private val table: LongArray = buildTable()

    private fun buildTable(): LongArray {
        val t = LongArray(256)
        for (i in 0 until 256) {
            var crc = i.toLong()
            repeat(8) {
                crc =
                    if (crc and 1L == 1L) {
                        (crc ushr 1) xor POLY
                    } else {
                        crc ushr 1
                    }
            }
            t[i] = crc
        }
        return t
    }

    override fun hash(content: String): String {
        var crc = 0L.inv() // Go's update starts from ^crc, with the initial crc == 0
        for (b in content.toByteArray(Charsets.UTF_8)) {
            val idx = ((crc.toInt() xor b.toInt()) and 0xFF)
            crc = table[idx] xor (crc ushr 8)
        }
        crc = crc.inv()
        // Go writes the 64-bit checksum as 8 big-endian bytes before hex-encoding.
        return uLongToHexLower(crc, byteCount = 8)
    }
}
