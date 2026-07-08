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
import java.util.Base64
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
 * Ciphertext is encoded as `base64url(nonce || ciphertext || tag)` — the same wire format Go
 * produces via `gcm.Seal(nonce, nonce, ...)` + `base64.URLEncoding`, so a fresh random nonce is
 * generated per [encrypt] call.
 *
 * @param observer the observability sink; defaults to a no-op. Tests inject a recording observer.
 */
public fun newAesEncryptorDecryptor(
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

    override suspend fun encrypt(content: String): String =
        o11y.span(NAME) {
            set(Keys.LENGTH, content.length)

            val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            val sealed = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

            Base64.getUrlEncoder().encodeToString(nonce + sealed)
        }

    override suspend fun decrypt(content: String): String =
        o11y.span(NAME) {
            set(Keys.LENGTH, content.length)

            // On any failure below we simply throw: the surrounding span records the exception once
            // (via acknowledge) and ends, mirroring Go's single `op.Error(...)` on the failure path.
            val ciphered = Base64.getUrlDecoder().decode(content)

            if (ciphered.size < NONCE_SIZE) {
                throw MalformedCiphertextException()
            }

            val nonce = ciphered.copyOfRange(0, NONCE_SIZE)
            val body = ciphered.copyOfRange(NONCE_SIZE, ciphered.size)

            val plaintext =
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                    cipher.doFinal(body)
                } catch (e: AEADBadTagException) {
                    throw AuthenticationFailedException().apply { initCause(e) }
                }

            String(plaintext, Charsets.UTF_8)
        }
}
