package com.primandproper.platform.cryptography.encryption.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** Mirrors platform-go's `encryption/config/config_test.go` (`TestProvideEncryptorDecryptor`). */
class ProvideTest {
    private val key = "blahblahblahblahblahblahblahblah".toByteArray()

    @Test
    fun aesProvider() {
        assertNotNull(EncryptorDecryptor(EncryptionConfig(provider = EncryptionProvider.AES), key))
    }

    @Test
    fun salsa20ProviderResolvesToSeam() {
        // salsa20 is a documented TODO seam: it resolves to a non-null instance (matching Go), but
        // encrypt/decrypt on it throw until implemented.
        assertNotNull(EncryptorDecryptor(EncryptionConfig(provider = EncryptionProvider.SALSA20), key))
    }

    @Test
    fun nilConfigErrors() {
        assertFailsWith<IllegalArgumentException> { EncryptorDecryptor(null, key) }
    }
}
