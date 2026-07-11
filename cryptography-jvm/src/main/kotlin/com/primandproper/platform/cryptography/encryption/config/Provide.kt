package com.primandproper.platform.cryptography.encryption.config

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.aes.aesEncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.salsa20.salsa20EncryptorDecryptor
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider

/**
 * Selects and builds an [EncryptorDecryptor] from [config]. Port of platform-go's
 * `config.ProvideEncryptorDecryptor`. The dispatch is exhaustive over [EncryptionProvider]: the
 * provider was already resolved from its string form at the parse edge, so there is no unknown arm.
 *
 * @throws IllegalArgumentException when [config] is null.
 */
public fun EncryptorDecryptor(
    config: EncryptionConfig?,
    key: ByteArray,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): EncryptorDecryptor {
    requireNotNull(config) { "nil config provided" }
    return when (config.provider) {
        EncryptionProvider.AES ->
            aesEncryptorDecryptor(key, Observer("aes_encryptor", logger, tracerProvider))
        EncryptionProvider.SALSA20 ->
            salsa20EncryptorDecryptor(key, Observer("salsa20_encryptor", logger, tracerProvider))
    }
}
