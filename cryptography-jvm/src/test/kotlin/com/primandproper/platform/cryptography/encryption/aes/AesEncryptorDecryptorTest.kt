package com.primandproper.platform.cryptography.encryption.aes

import com.primandproper.platform.cryptography.encryption.IncorrectKeyLengthException
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Mirrors platform-go's `encryption/aes/aes_test.go`. */
class AesEncryptorDecryptorTest {
    private val key = ByteArray(32) { (it * 7 + 3).toByte() }

    @Test
    fun rejectsIncorrectKeyLength() {
        assertFailsWith<IncorrectKeyLengthException> { aesEncryptorDecryptor(ByteArray(16)) }
    }

    @Test
    fun basicOperation() =
        runTest {
            val ed = aesEncryptorDecryptor(key)
            val expected = "basic operation"

            val encrypted = ed.encrypt(expected)
            assertNotEquals("", encrypted)
            assertNotEquals(expected, encrypted)

            // Fresh random nonce per call, so the same plaintext yields different ciphertexts.
            val encrypted2 = ed.encrypt(expected)
            assertNotEquals(encrypted, encrypted2)

            assertEquals(expected, ed.decrypt(encrypted))
            assertEquals(expected, ed.decrypt(encrypted2))
        }

    @Test
    fun decryptRejectsTamperedCiphertext() =
        runTest {
            val ed = aesEncryptorDecryptor(key)
            val encrypted = ed.encrypt("sensitive payload")

            val raw = Base64.getUrlDecoder().decode(encrypted)
            raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
            val tampered = Base64.getUrlEncoder().encodeToString(raw)

            assertFailsWith<Throwable> { ed.decrypt(tampered) }
        }

    @Test
    fun roundTripsBinaryPayloadLosslessly() =
        runTest {
            val ed = aesEncryptorDecryptor(key)
            // Every byte value, including bytes that are not valid UTF-8 — the whole point of the
            // ByteArray-primary surface is that these survive a round trip without corruption.
            val payload = ByteArray(256) { it.toByte() }

            val decrypted = ed.decrypt(ed.encrypt(payload))

            assertContentEquals(payload, decrypted)
        }

    @Test
    fun observesByteLengthOnEncrypt() =
        runTest {
            val obs = RecordingObserver()
            val ed = aesEncryptorDecryptor(key, obs)
            val payload = "observes byte length on encrypt".toByteArray()

            ed.encrypt(payload)

            // The span measures the raw payload it actually processed, not a String's char count.
            obs.assertObservedOperationWithValues(Keys.LENGTH to payload.size)
        }

    @Test
    fun observesLengthAndRecordsErrorOnBadDecrypt() =
        runTest {
            val obs = RecordingObserver()
            val ed = aesEncryptorDecryptor(key, obs)
            // A full-length but bogus ciphertext: passes the nonce-length gate, fails the GCM tag.
            val bad = ByteArray(32) { (it + 1).toByte() }

            assertFailsWith<Throwable> { ed.decrypt(bad) }

            obs.assertObservedOperationWithValues(Keys.LENGTH to bad.size)
            val op = obs.operations.single { it.values[Keys.LENGTH] == bad.size }
            assertEquals(1, op.errors.size)
        }

    @Test
    fun decryptTooShortForNonceRecordsError() =
        runTest {
            val obs = RecordingObserver()
            val ed = aesEncryptorDecryptor(key, obs)
            val tooShort = byteArrayOf(0, 1, 2)

            assertFailsWith<Throwable> { ed.decrypt(tooShort) }

            obs.assertObservedOperationWithValues(Keys.LENGTH to tooShort.size)
            val op = obs.operations.single { it.values[Keys.LENGTH] == tooShort.size }
            assertEquals(1, op.errors.size)
            assertTrue(op.ended)
        }
}
