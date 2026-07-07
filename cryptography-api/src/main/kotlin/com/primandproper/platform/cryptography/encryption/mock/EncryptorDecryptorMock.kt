package com.primandproper.platform.cryptography.encryption.mock

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor

/**
 * A configurable mock [EncryptorDecryptor] that records its calls. Port of platform-go's
 * moq-generated `EncryptorDecryptorMock`: supply [encryptFunc]/[decryptFunc] stubs and inspect
 * [encryptCalls]/[decryptCalls] afterwards. Calling a method whose stub is unset throws, matching the
 * Go mock's panic.
 */
public class EncryptorDecryptorMock(
    public var encryptFunc: (suspend (content: String) -> String)? = null,
    public var decryptFunc: (suspend (content: String) -> String)? = null,
) : EncryptorDecryptor {
    /** A recorded invocation of a mock method. */
    public data class Call(val content: String)

    private val lock = Any()
    private val recordedEncryptCalls = mutableListOf<Call>()
    private val recordedDecryptCalls = mutableListOf<Call>()

    public val encryptCalls: List<Call> get() = synchronized(lock) { recordedEncryptCalls.toList() }
    public val decryptCalls: List<Call> get() = synchronized(lock) { recordedDecryptCalls.toList() }

    override suspend fun encrypt(content: String): String {
        val fn =
            encryptFunc
                ?: error("EncryptorDecryptorMock.encryptFunc: method is nil but Encryptor.encrypt was just called")
        synchronized(lock) { recordedEncryptCalls += Call(content) }
        return fn(content)
    }

    override suspend fun decrypt(content: String): String {
        val fn =
            decryptFunc
                ?: error("EncryptorDecryptorMock.decryptFunc: method is nil but Decryptor.decrypt was just called")
        synchronized(lock) { recordedDecryptCalls += Call(content) }
        return fn(content)
    }
}
