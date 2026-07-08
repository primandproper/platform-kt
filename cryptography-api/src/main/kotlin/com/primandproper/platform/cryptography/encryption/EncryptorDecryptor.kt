package com.primandproper.platform.cryptography.encryption

/**
 * The secret key material used to derive encryption/decryption keys. A named wrapper over a
 * [ByteArray] (mirroring platform-go's `MasterKey []byte`) so dependency-injection lookups resolve it
 * distinctly and cannot collide with an arbitrary byte array registered in the same container.
 */
@JvmInline
public value class MasterKey(public val bytes: ByteArray)

/** Encrypts plaintext into an encoded ciphertext string. Port of platform-go's `Encryptor`. */
public interface Encryptor {
    public suspend fun encrypt(content: String): String
}

/** Decrypts an encoded ciphertext string back into plaintext. Port of platform-go's `Decryptor`. */
public interface Decryptor {
    public suspend fun decrypt(content: String): String
}

/** The combined authenticated-encryption surface. Port of platform-go's `EncryptorDecryptor`. */
public interface EncryptorDecryptor : Encryptor, Decryptor
