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
    private val lock = Any()
    private val _allowCalls = mutableListOf<String>()
    private var _closeCalls = 0

    public val allowCalls: List<String> get() = synchronized(lock) { _allowCalls.toList() }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override suspend fun allow(key: String): Boolean {
        synchronized(lock) { _allowCalls += key }
        val func = allowFunc ?: error("mock.allowFunc: method is null but was just called")
        return func.invoke(key)
    }

    override fun close() {
        synchronized(lock) { _closeCalls++ }
        closeFunc?.invoke()
    }
}
