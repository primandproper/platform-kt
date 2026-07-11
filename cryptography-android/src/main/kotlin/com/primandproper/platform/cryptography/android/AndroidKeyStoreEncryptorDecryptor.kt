package com.primandproper.platform.cryptography.android

import android.content.Context
import androidx.security.crypto.MasterKey
import com.primandproper.platform.cryptography.encryption.AuthenticationFailedException
import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val NAME = "android_keystore_encryptor"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = 128

/**
 * The default AndroidKeyStore alias for this module's AES-256-GCM key. Deliberately distinct from
 * [MasterKey.DEFAULT_MASTER_KEY_ALIAS] (which `:secrets-android`'s EncryptedSharedPreferences uses) so
 * the two modules never share a key. Overridable via [androidKeyStoreEncryptorDecryptor]'s `keyAlias`.
 */
public const val DEFAULT_ENCRYPTOR_KEY_ALIAS: String = "platform_cryptography_master_key"

/**
 * Builds an [EncryptorDecryptor] whose AES-256-GCM key is provisioned and held in the AndroidKeyStore
 * via Jetpack Security's [MasterKey] (`AES256_GCM` scheme). The key is non-exportable: encryption and
 * decryption run inside the keystore, and only the ciphertext (framed raw as `iv || body`, which the
 * String convenience base64url-encodes) crosses the app boundary.
 *
 * The keystore generates a fresh random IV per [EncryptorDecryptor.encrypt], mirroring the AES-GCM
 * semantics of the :cryptography-jvm backend.
 *
 * TODO(alternate-schemes): only the default `AES256_GCM` MasterKey scheme is wired; StrongBox-backed
 * and user-authentication-gated keys are left as a seam.
 */
public fun androidKeyStoreEncryptorDecryptor(
    context: Context,
    keyAlias: String = DEFAULT_ENCRYPTOR_KEY_ALIAS,
    observer: Observer = noopObserver(NAME),
): EncryptorDecryptor {
    // Provisioning the MasterKey creates (idempotently) the AES-256-GCM key under [keyAlias] in the
    // AndroidKeyStore; we then load that same keystore entry as a SecretKey for the Cipher.
    MasterKey.Builder(context, keyAlias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    val secretKey = keyStore.getKey(keyAlias, null) as SecretKey

    return AndroidKeyStoreEncryptorDecryptor(secretKey, observer)
}

internal class AndroidKeyStoreEncryptorDecryptor(
    private val secretKey: SecretKey,
    private val o11y: Observer,
) : EncryptorDecryptor {
    override suspend fun encrypt(plaintext: ByteArray): ByteArray =
        o11y.span(NAME) {
            set(Keys.LENGTH, plaintext.size)

            // Keystore ciphers run in the AndroidKeyStore process and block the calling thread; hop to
            // IO so a suspend caller on the main thread is never blocked.
            withContext(Dispatchers.IO) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                // AndroidKeyStore requires the keystore to generate the GCM IV; read it back afterwards.
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val body = cipher.doFinal(plaintext)

                Framing.frame(cipher.iv, body)
            }
        }

    override suspend fun decrypt(ciphertext: ByteArray): ByteArray =
        o11y.span(NAME) {
            set(Keys.LENGTH, ciphertext.size)

            val (nonce, body) = Framing.unframe(ciphertext)
            withContext(Dispatchers.IO) {
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
                    cipher.doFinal(body)
                } catch (e: AEADBadTagException) {
                    throw AuthenticationFailedException().apply { initCause(e) }
                }
            }
        }
}
