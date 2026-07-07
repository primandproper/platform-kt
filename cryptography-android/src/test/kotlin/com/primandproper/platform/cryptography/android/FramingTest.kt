package com.primandproper.platform.cryptography.android

import com.primandproper.platform.cryptography.encryption.MalformedCiphertextException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * Off-device unit tests for the ciphertext framing. The AndroidKeyStore transform itself requires a
 * device/emulator, but the wire-format layout is pure JVM and covered here.
 */
class FramingTest {
    @Test
    fun encodeDecodeRoundTrips() {
        val nonce = ByteArray(Framing.NONCE_SIZE) { it.toByte() }
        val body = "ciphertext-and-tag".toByteArray()

        val (decodedNonce, decodedBody) = Framing.decode(Framing.encode(nonce, body))

        assertContentEquals(nonce, decodedNonce)
        assertContentEquals(body, decodedBody)
    }

    @Test
    fun decodeRejectsCiphertextTooShortForNonce() {
        val tooShort = Base64.getUrlEncoder().encodeToString(byteArrayOf(0, 1, 2))
        assertFailsWith<MalformedCiphertextException> { Framing.decode(tooShort) }
    }
}
