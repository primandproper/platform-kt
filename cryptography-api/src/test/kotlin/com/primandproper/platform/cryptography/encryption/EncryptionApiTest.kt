package com.primandproper.platform.cryptography.encryption

import com.primandproper.platform.cryptography.encryption.config.EncryptionProvider
import com.primandproper.platform.cryptography.encryption.mock.EncryptorDecryptorMock
import com.primandproper.platform.cryptography.encryption.noop.noopEncryptorDecryptor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ErrorsTest {
    @Test
    fun incorrectKeyLengthMessage() {
        assertEquals("secret is not the right length", IncorrectKeyLengthException().message)
    }

    @Test
    fun malformedCiphertextMessage() {
        assertEquals("malformed ciphertext", MalformedCiphertextException().message)
    }

    @Test
    fun authenticationFailedMessage() {
        assertEquals("ciphertext authentication failed", AuthenticationFailedException().message)
    }
}

class EncryptionProviderTest {
    @Test
    fun knownProvidersResolveFromValue() {
        assertEquals(EncryptionProvider.AES, EncryptionProvider.fromValue("aes"))
        assertEquals(EncryptionProvider.SALSA20, EncryptionProvider.fromValue("salsa20"))
    }

    @Test
    fun fromValueTrimsAndLowercases() {
        assertEquals(EncryptionProvider.AES, EncryptionProvider.fromValue("  AES "))
    }

    @Test
    fun emptyProviderResolvesToNull() {
        assertNull(EncryptionProvider.fromValue(""))
    }

    @Test
    fun unknownProviderResolvesToNull() {
        assertNull(EncryptionProvider.fromValue("invalid"))
    }
}

class NoopEncryptorDecryptorTest {
    @Test
    fun roundTripsUnchanged() =
        runTest {
            val ed = noopEncryptorDecryptor()
            assertEquals("hello", ed.encrypt("hello"))
            assertEquals("hello", ed.decrypt("hello"))
        }
}

class EncryptorDecryptorMockTest {
    @Test
    fun recordsCallsAndDelegatesToStubs() =
        runTest {
            val mock =
                EncryptorDecryptorMock(
                    encryptFunc = { "enc:$it" },
                    decryptFunc = { it.removePrefix("enc:") },
                )

            assertEquals("enc:payload", mock.encrypt("payload"))
            assertEquals("payload", mock.decrypt("enc:payload"))

            assertEquals(listOf(EncryptorDecryptorMock.Call("payload")), mock.encryptCalls)
            assertEquals(listOf(EncryptorDecryptorMock.Call("enc:payload")), mock.decryptCalls)
        }

    @Test
    fun unsetStubThrows() =
        runTest {
            assertFailsWith<IllegalStateException> { EncryptorDecryptorMock().encrypt("x") }
        }
}
