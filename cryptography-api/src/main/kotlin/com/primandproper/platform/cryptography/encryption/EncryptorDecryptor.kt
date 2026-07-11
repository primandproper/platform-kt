package com.primandproper.platform.cryptography.encryption

import java.util.Base64

/**
 * The secret key material used to derive encryption/decryption keys. A named wrapper over a
 * [ByteArray] (mirroring platform-go's `MasterKey []byte`) so dependency-injection lookups resolve it
 * distinctly and cannot collide with an arbitrary byte array registered in the same container.
 *
 * Equality is by key-material content (via [ByteArray.contentEquals]), not array identity: two
 * wrappers over structurally-equal bytes compare equal and hash alike, so a [MasterKey] is safe to
 * use as a map key. (A plain `@JvmInline value class` over a [ByteArray] would inherit array
 * reference-equality, which this deliberately overrides.)
 */
public class MasterKey(public val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = this === other || (other is MasterKey && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * Encrypts plaintext into ciphertext. Port of platform-go's `Encryptor`.
 *
 * The primary [encrypt] operates on raw [ByteArray] payloads, so binary plaintext is never forced
 * through a lossy [String] round-trip. The [String] convenience UTF-8-encodes the plaintext and
 * base64url-encodes the resulting ciphertext (the historical wire format).
 */
public fun interface Encryptor {
    /** Encrypts raw [plaintext] bytes, returning the raw ciphertext bytes. */
    public suspend fun encrypt(plaintext: ByteArray): ByteArray

    /** UTF-8-encodes [content], encrypts it, and base64url-encodes the ciphertext. */
    public suspend fun encrypt(content: String): String =
        Base64.getUrlEncoder().encodeToString(encrypt(content.toByteArray(Charsets.UTF_8)))
}

/**
 * Decrypts ciphertext back into plaintext. Port of platform-go's `Decryptor`.
 *
 * The primary [decrypt] operates on raw [ByteArray] ciphertext; the [String] convenience
 * base64url-decodes its argument and UTF-8-decodes the recovered plaintext.
 */
public fun interface Decryptor {
    /** Decrypts raw [ciphertext] bytes, returning the raw plaintext bytes. */
    public suspend fun decrypt(ciphertext: ByteArray): ByteArray

    /** base64url-decodes [content], decrypts it, and UTF-8-decodes the plaintext. */
    public suspend fun decrypt(content: String): String = String(decrypt(Base64.getUrlDecoder().decode(content)), Charsets.UTF_8)
}

/** The combined authenticated-encryption surface. Port of platform-go's `EncryptorDecryptor`. */
public interface EncryptorDecryptor : Encryptor, Decryptor
