package com.primandproper.platform.cryptography.encryption.config

/** The supported encryption provider identifiers. Mirrors platform-go's `ProviderAES`/`ProviderSalsa20`. */
public object Provider {
    /** The AES-GCM encryption provider. */
    public const val AES: String = "aes"

    /** The Salsa20 (XSalsa20-Poly1305) encryption provider. */
    public const val SALSA20: String = "salsa20"
}

/**
 * Configuration for the encryption provider. Port of platform-go's `config.Config`.
 *
 * The concrete wiring — `provideEncryptorDecryptor` — lives in `:cryptography-jvm`, where the AES
 * backend is available; this API module holds only the data and its validation.
 */
public data class Config(
    val provider: String = "",
) {
    /**
     * Validates the config, throwing [IllegalArgumentException] when [provider] is not one of the
     * supported providers. Mirrors `Config.ValidateWithContext` (Required + In(aes, salsa20)).
     */
    public fun validate() {
        require(provider == Provider.AES || provider == Provider.SALSA20) {
            "provider must be one of \"${Provider.AES}\", \"${Provider.SALSA20}\""
        }
    }
}
