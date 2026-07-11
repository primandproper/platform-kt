package com.primandproper.platform.cryptography.encryption.config

/**
 * The supported encryption providers. Mirrors platform-go's `ProviderAES`/`ProviderSalsa20`. [value]
 * is the wire/string form validated against configuration.
 */
public enum class EncryptionProvider(
    public val value: String,
) {
    /** The AES-GCM encryption provider. */
    AES("aes"),

    /** The Salsa20 (XSalsa20-Poly1305) encryption provider. */
    SALSA20("salsa20"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — the parse edge where a raw config string becomes the typed enum.
         * Mirrors Go's `validation.In(ProviderAES, ProviderSalsa20)` rejecting an unknown name (an
         * empty string included, since it matches nothing).
         */
        public fun fromValue(value: String): EncryptionProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Configuration for the encryption provider. Port of platform-go's `config.Config`.
 *
 * [provider] is a typed [EncryptionProvider] and required (Go's `ValidateWithContext` treats an empty
 * provider as invalid), so the Required + `In(aes, salsa20)` validation Go performs is enforced by the
 * type: a raw string is turned into the enum once, at [EncryptionProvider.fromValue] (the parse edge),
 * and everything downstream consumes the typed value.
 *
 * The concrete wiring — `EncryptorDecryptor` — lives in `:cryptography-jvm`, where the AES
 * backend is available; this API module holds only the data.
 */
public data class EncryptionConfig(
    val provider: EncryptionProvider,
)
