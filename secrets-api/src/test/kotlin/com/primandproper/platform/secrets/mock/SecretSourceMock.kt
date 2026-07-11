package com.primandproper.platform.secrets.mock

import com.primandproper.platform.secrets.SecretSource

/**
 * A configurable [SecretSource] test double, in the style of platform-go's moq-generated mocks (the
 * secrets package's tests inject fake clients the same way). Each method delegates to a settable
 * `...Func`; calling [getSecret] while [getSecretFunc] is `null` throws [IllegalStateException] — the
 * "unmocked call surfaces immediately" behavior moq's generated panic gives. Every call is recorded
 * in the matching `...Calls` list.
 *
 * Recording is guarded by a per-mock lock; the public accessors hand back an immutable snapshot, so a
 * recorder on one thread can't trip a reader iterating the calls on another.
 *
 * ```
 * val mock = SecretSourceMock(getSecretFunc = { key -> "value-for-$key" })
 * ```
 */
public class SecretSourceMock(
    public var getSecretFunc: (suspend (String) -> String)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : SecretSource {
    private val lock = Any()

    private val _getSecretCalls = mutableListOf<String>()
    private var _closeCalls = 0

    public val getSecretCalls: List<String> get() = synchronized(lock) { _getSecretCalls.toList() }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override suspend fun getSecret(name: String): String {
        synchronized(lock) { _getSecretCalls += name }
        val func = getSecretFunc ?: error("SecretSourceMock.getSecretFunc: method is null but was just called")
        return func.invoke(name)
    }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        closeFunc?.invoke()
    }
}
