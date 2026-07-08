package com.primandproper.platform.ratelimiting.mock

import com.primandproper.platform.ratelimiting.RateLimiter

/**
 * A configurable [RateLimiter] test double, in the spirit of platform-go's moq-generated mocks (the
 * `ratelimiting` package has no checked-in mock; this fills the same role for Kotlin call sites). Each
 * method delegates to a settable `...Func`; calling [allow] whose `allowFunc` was left `null` throws
 * [IllegalStateException], the "unmocked call surfaces immediately" behavior moq's generated panic
 * gives. Every call is recorded so tests can assert what was invoked.
 *
 * ```
 * val mock = RateLimiterMock(allowFunc = { key -> key != "blocked" })
 * ```
 */
public class RateLimiterMock(
    public var allowFunc: (suspend (String) -> Boolean)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : RateLimiter {
    public val allowCalls: MutableList<String> = mutableListOf()
    public var closeCalls: Int = 0
        private set

    override suspend fun allow(key: String): Boolean {
        allowCalls += key
        val func = allowFunc ?: error("mock.allowFunc: method is null but was just called")
        return func.invoke(key)
    }

    override fun close() {
        closeCalls++
        closeFunc?.invoke()
    }
}
