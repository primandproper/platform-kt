package com.primandproper.platform.cryptography.encryption.salsa20

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.IncorrectKeyLengthException
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver

private const val NAME = "salsa20_encryptor"
private const val KEY_LENGTH = 32

/**
 * Builds the Salsa20 (NaCl secretbox / XSalsa20-Poly1305) [EncryptorDecryptor], the port of
 * platform-go's `salsa20.NewEncryptorDecryptor`.
 *
 * TODO(salsa20): the actual XSalsa20-Poly1305 transform is an unimplemented seam. Construction
 * validates the key length so provider wiring resolves a non-null instance (matching Go's config
 * behaviour), but [encrypt]/[decrypt] throw [NotImplementedError]. Wiring in a pure-Kotlin secretbox
 * (or `org.bouncycastle` XSalsa20/Poly1305) is left as follow-up work; AES-GCM is the recommended
 * backend in the meantime.
 */
public fun newSalsa20EncryptorDecryptor(
    key: ByteArray,
    @Suppress("UNUSED_PARAMETER") observer: Observer = noopObserver(NAME),
): EncryptorDecryptor {
    if (key.size != KEY_LENGTH) throw IncorrectKeyLengthException()
    return Salsa20EncryptorDecryptor
}

private object Salsa20EncryptorDecryptor : EncryptorDecryptor {
    override suspend fun encrypt(content: String): String =
        TODO("salsa20: XSalsa20-Poly1305 (NaCl secretbox) encryption is not yet implemented")

    override suspend fun decrypt(content: String): String =
        TODO("salsa20: XSalsa20-Poly1305 (NaCl secretbox) decryption is not yet implemented")
}
