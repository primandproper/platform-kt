package com.primandproper.platform.cryptography.encryption

import com.primandproper.platform.cryptography.encryption.config.Config
import com.primandproper.platform.cryptography.encryption.config.Provider
import com.primandproper.platform.cryptography.encryption.mock.EncryptorDecryptorMock
import com.primandproper.platform.cryptography.encryption.noop.noopEncryptorDecryptor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

class ConfigValidateTest {
    @Test
    fun aesProviderValidates() {
        Config(provider = Provider.AES).validate()
    }

    @Test
    fun salsa20ProviderValidates() {
        Config(provider = Provider.SALSA20).validate()
    }

    @Test
    fun emptyProviderErrors() {
        assertFailsWith<IllegalArgumentException> { Config().validate() }
    }

    @Test
    fun invalidProviderErrors() {
        assertFailsWith<IllegalArgumentException> { Config(provider = "invalid").validate() }
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
