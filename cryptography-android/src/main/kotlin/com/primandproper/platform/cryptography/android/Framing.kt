package com.primandproper.platform.cryptography.android

import com.primandproper.platform.cryptography.encryption.MalformedCiphertextException
import java.util.Base64

/**
 * The on-the-wire ciphertext framing, factored out so it is unit-testable off-device: a GCM nonce
 * prepended to the ciphertext-plus-tag, base64url-encoded. Matches the `base64url(nonce || body)`
 * layout the platform-go and :cryptography-jvm AES backends produce.
 */
internal object Framing {
    /** AndroidKeyStore AES-GCM uses a 12-byte IV. */
    const val NONCE_SIZE: Int = 12

    fun encode(
        nonce: ByteArray,
        body: ByteArray,
    ): String = Base64.getUrlEncoder().encodeToString(nonce + body)

    /** Splits an encoded ciphertext into (nonce, body), or throws [MalformedCiphertextException]. */
    fun decode(encoded: String): Pair<ByteArray, ByteArray> {
        val raw = Base64.getUrlDecoder().decode(encoded)
        if (raw.size < NONCE_SIZE) throw MalformedCiphertextException()
        return raw.copyOfRange(0, NONCE_SIZE) to raw.copyOfRange(NONCE_SIZE, raw.size)
    }
}
