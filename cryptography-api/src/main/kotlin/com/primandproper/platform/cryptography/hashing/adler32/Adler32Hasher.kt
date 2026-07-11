package com.primandproper.platform.cryptography.hashing.adler32

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.uLongToBytes
import java.util.zip.Adler32

/**
 * A [Hasher] backed by the Adler-32 checksum. Port of platform-go's
 * `adler32.NewAdler32Hasher`.
 *
 * WARNING: Adler-32 is a NON-CRYPTOGRAPHIC checksum and MUST NOT be used for security, password, or
 * tamper-resistance purposes.
 */
public object Adler32Hasher : Hasher {
    override fun hash(content: ByteArray): ByteArray {
        val checksum = Adler32()
        checksum.update(content)
        // Go's hash writes the 32-bit checksum as 4 big-endian bytes, so the hex-encoded String
        // convenience is always 8 zero-padded characters.
        return uLongToBytes(checksum.value, byteCount = 4)
    }
}
