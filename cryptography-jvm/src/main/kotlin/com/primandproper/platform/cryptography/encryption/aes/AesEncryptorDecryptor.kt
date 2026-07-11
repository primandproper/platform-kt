package com.primandproper.platform.cryptography.encryption.aes

import com.primandproper.platform.cryptography.encryption.AuthenticationFailedException
import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.IncorrectKeyLengthException
import com.primandproper.platform.cryptography.encryption.MalformedCiphertextException
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val NAME = "aes_encryptor"

// AES-GCM parameters chosen to match Go's crypto/cipher NewGCM defaults: a 12-byte nonce and a
// 128-bit authentication tag.
private const val KEY_LENGTH = 32
private const val NONCE_SIZE = 12
private const val TAG_BITS = 128

/**
 * Builds an AES-256-GCM [EncryptorDecryptor], the port of platform-go's `aes.NewEncryptorDecryptor`.
 *
 * The [key] must be exactly 32 bytes (AES-256); otherwise [IncorrectKeyLengthException] is thrown.
 * Raw ciphertext is laid out as `nonce || ciphertext || tag`; the [Encryptor.encrypt] String
 * convenience base64url-encodes it — the same wire format Go produces via `gcm.Seal(nonce, nonce,
 * ...)` + `base64.URLEncoding`. A fresh random nonce is generated per encrypt call.
 *
 * @param observer the observability sink; defaults to a no-op. Tests inject a recording observer.
 */
public fun aesEncryptorDecryptor(
    key: ByteArray,
    observer: Observer = noopObserver(NAME),
): EncryptorDecryptor {
    if (key.size != KEY_LENGTH) throw IncorrectKeyLengthException()
    return AesEncryptorDecryptor(key.copyOf(), observer)
}

private class AesEncryptorDecryptor(
    private val key: ByteArray,
    private val o11y: Observer,
) : EncryptorDecryptor {
    private val random = SecureRandom()

    override suspend fun encrypt(plaintext: ByteArray): ByteArray =
        o11y.span(NAME) {
            set(Keys.LENGTH, plaintext.size)

            val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            val sealed = cipher.doFinal(plaintext)

            nonce + sealed
        }

    override suspend fun decrypt(ciphertext: ByteArray): ByteArray =
        o11y.span(NAME) {
            set(Keys.LENGTH, ciphertext.size)

            // On any failure below we simply throw: the surrounding span records the exception once
            // (via acknowledge) and ends, mirroring Go's single `op.Error(...)` on the failure path.
            if (ciphertext.size < NONCE_SIZE) {
                throw MalformedCiphertextException()
            }

            val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
            val body = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)

            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.doFinal(body)
            } catch (e: AEADBadTagException) {
                throw AuthenticationFailedException().apply { initCause(e) }
            }
        }
}
