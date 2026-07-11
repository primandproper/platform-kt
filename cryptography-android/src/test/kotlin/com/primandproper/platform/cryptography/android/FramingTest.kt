package com.primandproper.platform.cryptography.android

import com.primandproper.platform.cryptography.encryption.MalformedCiphertextException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * Off-device unit tests for the ciphertext framing. The AndroidKeyStore transform itself requires a
 * device/emulator, but the raw wire-format layout is pure JVM and covered here.
 */
class FramingTest {
    @Test
    fun frameUnframeRoundTrips() {
        val nonce = ByteArray(Framing.NONCE_SIZE) { it.toByte() }
        val body = "ciphertext-and-tag".toByteArray()

        val (decodedNonce, decodedBody) = Framing.unframe(Framing.frame(nonce, body))

        assertContentEquals(nonce, decodedNonce)
        assertContentEquals(body, decodedBody)
    }

    @Test
    fun unframeRejectsCiphertextTooShortForNonce() {
        assertFailsWith<MalformedCiphertextException> { Framing.unframe(byteArrayOf(0, 1, 2)) }
    }
}
