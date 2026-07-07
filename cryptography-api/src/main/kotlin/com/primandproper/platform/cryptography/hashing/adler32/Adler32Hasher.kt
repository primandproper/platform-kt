package com.primandproper.platform.cryptography.hashing.adler32

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.uLongToHexLower
import java.util.zip.Adler32

/**
 * Returns a [Hasher] backed by the Adler-32 checksum. Port of platform-go's
 * `adler32.NewAdler32Hasher`.
 *
 * WARNING: Adler-32 is a NON-CRYPTOGRAPHIC checksum and MUST NOT be used for security, password, or
 * tamper-resistance purposes.
 */
public fun newAdler32Hasher(): Hasher = Adler32Hasher

private object Adler32Hasher : Hasher {
    override fun hash(content: String): String {
        val checksum = Adler32()
        checksum.update(content.toByteArray(Charsets.UTF_8))
        // Go's hash writes the 32-bit checksum as 4 big-endian bytes before hex-encoding, so the
        // output is always 8 zero-padded hex characters.
        return uLongToHexLower(checksum.value, byteCount = 4)
    }
}
