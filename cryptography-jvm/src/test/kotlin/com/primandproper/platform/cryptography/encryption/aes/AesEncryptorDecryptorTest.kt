package com.primandproper.platform.cryptography.encryption.aes

import com.primandproper.platform.cryptography.encryption.IncorrectKeyLengthException
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Mirrors platform-go's `encryption/aes/aes_test.go`. */
class AesEncryptorDecryptorTest {
    private val key = ByteArray(32) { (it * 7 + 3).toByte() }

    @Test
    fun rejectsIncorrectKeyLength() {
        assertFailsWith<IncorrectKeyLengthException> { newAesEncryptorDecryptor(ByteArray(16)) }
    }

    @Test
    fun basicOperation() =
        runTest {
            val ed = newAesEncryptorDecryptor(key)
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
            val ed = newAesEncryptorDecryptor(key)
            val encrypted = ed.encrypt("sensitive payload")

            val raw = Base64.getUrlDecoder().decode(encrypted)
            raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
            val tampered = Base64.getUrlEncoder().encodeToString(raw)

            assertFailsWith<Throwable> { ed.decrypt(tampered) }
        }

    @Test
    fun observesContentLengthOnEncrypt() =
        runTest {
            val obs = RecordingObserver()
            val ed = newAesEncryptorDecryptor(key, obs)
            val expected = "observes content length on encrypt"

            ed.encrypt(expected)

            obs.assertObservedOperationWithValues(Keys.LENGTH to expected.length)
        }

    @Test
    fun observesLengthAndRecordsErrorOnBadDecrypt() =
        runTest {
            val obs = RecordingObserver()
            val ed = newAesEncryptorDecryptor(key, obs)
            val bad = "!!!not-base64!!!"

            assertFailsWith<Throwable> { ed.decrypt(bad) }

            obs.assertObservedOperationWithValues(Keys.LENGTH to bad.length)
            val op = obs.operations.single { it.values[Keys.LENGTH] == bad.length }
            assertEquals(1, op.errors.size)
        }

    @Test
    fun decryptTooShortForNonceRecordsError() =
        runTest {
            val obs = RecordingObserver()
            val ed = newAesEncryptorDecryptor(key, obs)
            val tooShort = Base64.getUrlEncoder().encodeToString(byteArrayOf(0, 1, 2))

            assertFailsWith<Throwable> { ed.decrypt(tooShort) }

            obs.assertObservedOperationWithValues(Keys.LENGTH to tooShort.length)
            val op = obs.operations.single { it.values[Keys.LENGTH] == tooShort.length }
            assertEquals(1, op.errors.size)
            assertTrue(op.ended)
        }
}
