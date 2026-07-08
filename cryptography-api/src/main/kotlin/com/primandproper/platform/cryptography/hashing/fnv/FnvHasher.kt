package com.primandproper.platform.cryptography.hashing.fnv

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.uLongToHexLower

/**
 * Returns a [Hasher] backed by the FNV-1a (128-bit) hash, matching platform-go's
 * `fnv.NewFNVHasher` (which uses `fnv.New128a`) byte-for-byte.
 *
 * WARNING: FNV-1a is a NON-CRYPTOGRAPHIC hash and MUST NOT be used for security, password, or
 * tamper-resistance purposes.
 */
public fun newFNVHasher(): Hasher = FnvHasher

private object FnvHasher : Hasher {
    // FNV-1a 128-bit offset basis, split into high/low 64-bit words (Go's offset128Higher/Lower).
    private const val OFFSET_HIGH = 0x6c62272e07bb0142L
    private const val OFFSET_LOW = 0x62b821756295c58dL

    // FNV-1a 128-bit prime == 2^88 + 2^8 + 0x3b, represented as Go does: a low word and a shift.
    private const val PRIME_LOW = 0x13BL
    private const val PRIME_SHIFT = 24

    override fun hash(content: String): String {
        // s0 is the high 64 bits, s1 the low 64 bits, mirroring Go's `sum128a` [2]uint64.
        var s0 = OFFSET_HIGH
        var s1 = OFFSET_LOW
        for (b in content.toByteArray(Charsets.UTF_8)) {
            s1 = s1 xor (b.toLong() and 0xFF)
            // Full 128-bit product of PRIME_LOW * s1, matching Go's bits.Mul64 + carry-in.
            val lo = PRIME_LOW * s1 // low 64 bits (wraps naturally)
            val hi = unsignedMulHigh(PRIME_LOW, s1) // high 64 bits
            s0 = hi + (s1 shl PRIME_SHIFT) + (PRIME_LOW * s0)
            s1 = lo
        }
        // Go writes s0 then s1 as big-endian 8-byte words (16 bytes total) before hex-encoding.
        return uLongToHexLower(s0, byteCount = 8) + uLongToHexLower(s1, byteCount = 8)
    }

    /**
     * Unsigned high 64 bits of `a * b` where [a] is the small FNV prime low word (fits in 32 bits).
     * Splitting [b] into 32-bit halves keeps every partial product well within a signed Long.
     */
    private fun unsignedMulHigh(
        a: Long,
        b: Long,
    ): Long {
        val bHigh = b ushr 32
        val bLow = b and 0xFFFFFFFFL
        val partialHigh = a * bHigh
        val partialLow = a * bLow
        return (partialHigh + (partialLow ushr 32)) ushr 32
    }
}
