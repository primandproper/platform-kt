package com.primandproper.platform.secrets.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.secrets.SecretNotFoundException
import com.primandproper.platform.secrets.SecretSource

/**
 * An Android [SecretSource] backed by the hardware-backed Android Keystore. Secrets live in an
 * [EncryptedSharedPreferences] file whose keys and values are encrypted with a [MasterKey] that never
 * leaves the Keystore (AES-256-GCM). This is the on-device analog of platform-go's server-side
 * backends: the same interface, a client-appropriate store.
 *
 * ## Secret values are never observed
 * Like [com.primandproper.platform.secrets.env.EnvSecretSource], every operation records only the
 * lookup key on its span — never the (plaintext) secret value. The value exists in memory only long
 * enough to hand back to the caller; it is never passed to the observability pillars.
 *
 * The store logic is written against the [SharedPreferences] interface, not the encrypted
 * implementation directly, so it is exercised in JVM unit tests with an in-memory fake while
 * production wiring goes through [create], which builds the real Keystore-backed store.
 *
 * @constructor Internal seam taking an already-built [SharedPreferences] and [Observer] so tests can
 *   inject a fake without an Android runtime / Keystore.
 */
public class KeystoreSecretStore internal constructor(
    private val prefs: SharedPreferences,
    private val o11y: Observer,
) : SecretSource {
    override suspend fun getSecret(name: String): String =
        o11y.span("GetSecret") {
            // NOTE: only the secret's lookup key is observed, never its value.
            set("secret_key", name)
            if (!prefs.contains(name)) {
                throw error(SecretNotFoundException(name), "secret not present in keystore store")
            }
            prefs.getString(name, null) ?: throw error(
                SecretNotFoundException(name),
                "secret not present in keystore store",
            )
        }

    /**
     * Stores (or overwrites) the secret [value] under [name]. Beyond platform-go's read-only
     * `SecretSource` surface — an on-device store has to be populated somehow — but the observability
     * discipline is the same: only the key is recorded, never [value].
     */
    public suspend fun putSecret(
        name: String,
        value: String,
    ): Unit =
        o11y.span("PutSecret") {
            set("secret_key", name)
            prefs.edit().putString(name, value).apply()
        }

    /** Removes the secret under [name] if present. Records only the key. */
    public suspend fun removeSecret(name: String): Unit =
        o11y.span("RemoveSecret") {
            set("secret_key", name)
            prefs.edit().remove(name).apply()
        }

    override fun close() {
        o11y.logger.debug("closing keystore secret store")
    }

    public companion object {
        /** Component name applied to the logger and tracer. */
        public const val NAME: String = "keystore_secret_source"

        /** Default file name for the encrypted preferences store. */
        public const val DEFAULT_FILE_NAME: String = "platform_secrets"

        /**
         * Builds a production [KeystoreSecretStore] backed by [EncryptedSharedPreferences] and a
         * Keystore-resident [MasterKey]. Call once and reuse; construction touches the Keystore.
         *
         * @param context an Android [Context] (application context recommended).
         * @param fileName the encrypted preferences file name; defaults to [DEFAULT_FILE_NAME].
         */
        public fun create(
            context: Context,
            fileName: String = DEFAULT_FILE_NAME,
            logger: Logger? = null,
            tracerProvider: TracerProvider? = null,
        ): KeystoreSecretStore {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )

            return KeystoreSecretStore(prefs, Observer(NAME, logger, tracerProvider))
        }
    }
}
