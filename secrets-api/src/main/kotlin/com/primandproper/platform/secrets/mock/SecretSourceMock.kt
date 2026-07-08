package com.primandproper.platform.secrets.mock

import com.primandproper.platform.secrets.SecretSource

/**
 * A configurable [SecretSource] test double, in the style of platform-go's moq-generated mocks (the
 * secrets package's tests inject fake clients the same way). Each method delegates to a settable
 * `...Func`; calling [getSecret] while [getSecretFunc] is `null` throws [IllegalStateException] — the
 * "unmocked call surfaces immediately" behavior moq's generated panic gives. Every call is recorded
 * in the matching `...Calls` list.
 *
 * ```
 * val mock = SecretSourceMock(getSecretFunc = { key -> "value-for-$key" })
 * ```
 */
public class SecretSourceMock(
    public var getSecretFunc: (suspend (String) -> String)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : SecretSource {
    public val getSecretCalls: MutableList<String> = mutableListOf()
    public var closeCalls: Int = 0
        private set

    override suspend fun getSecret(name: String): String {
        getSecretCalls += name
        val func = getSecretFunc ?: error("SecretSourceMock.getSecretFunc: method is null but was just called")
        return func.invoke(name)
    }

    override fun close() {
        closeCalls++
        closeFunc?.invoke()
    }
}
