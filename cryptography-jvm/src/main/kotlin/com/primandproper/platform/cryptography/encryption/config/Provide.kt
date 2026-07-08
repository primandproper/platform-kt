package com.primandproper.platform.cryptography.encryption.config

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.aes.newAesEncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.salsa20.newSalsa20EncryptorDecryptor
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider

/**
 * Selects and builds an [EncryptorDecryptor] from [config]. Port of platform-go's
 * `config.ProvideEncryptorDecryptor`.
 *
 * @throws IllegalArgumentException when [config] is null or names an unknown provider.
 */
public fun provideEncryptorDecryptor(
    config: Config?,
    key: ByteArray,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): EncryptorDecryptor {
    requireNotNull(config) { "nil config provided" }
    return when (config.provider.trim().lowercase()) {
        Provider.AES ->
            newAesEncryptorDecryptor(key, Observer("aes_encryptor", logger, tracerProvider))
        Provider.SALSA20 ->
            newSalsa20EncryptorDecryptor(key, Observer("salsa20_encryptor", logger, tracerProvider))
        else ->
            throw IllegalArgumentException("unknown encryption provider: \"${config.provider}\"")
    }
}
