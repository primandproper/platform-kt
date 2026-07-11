package com.primandproper.platform.cryptography.android

import com.primandproper.platform.cryptography.encryption.MalformedCiphertextException

/**
 * The raw ciphertext framing, factored out so it is unit-testable off-device: a GCM nonce prepended
 * to the ciphertext-plus-tag. Matches the `nonce || body` layout the platform-go and :cryptography-jvm
 * AES backends produce; base64url-encoding of the full frame is handled by the [Encryptor]/[Decryptor]
 * String conveniences, so the on-the-wire string is identical across backends.
 */
internal object Framing {
    /** AndroidKeyStore AES-GCM uses a 12-byte IV. */
    const val NONCE_SIZE: Int = 12

    fun frame(
        nonce: ByteArray,
        body: ByteArray,
    ): ByteArray = nonce + body

    /** Splits a raw ciphertext frame into (nonce, body), or throws [MalformedCiphertextException]. */
    fun unframe(raw: ByteArray): Pair<ByteArray, ByteArray> {
        if (raw.size < NONCE_SIZE) throw MalformedCiphertextException()
        return raw.copyOfRange(0, NONCE_SIZE) to raw.copyOfRange(NONCE_SIZE, raw.size)
    }
}
