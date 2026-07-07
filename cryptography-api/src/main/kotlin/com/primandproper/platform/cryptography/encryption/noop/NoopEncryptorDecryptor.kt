package com.primandproper.platform.cryptography.encryption.noop

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor

/**
 * An [EncryptorDecryptor] that returns its input unchanged. Useful as a null-object in tests or
 * local development where real encryption is unwanted. It provides NO confidentiality and MUST NOT
 * be used in production.
 */
public fun noopEncryptorDecryptor(): EncryptorDecryptor = NoopEncryptorDecryptor

private object NoopEncryptorDecryptor : EncryptorDecryptor {
    override suspend fun encrypt(content: String): String = content

    override suspend fun decrypt(content: String): String = content
}
