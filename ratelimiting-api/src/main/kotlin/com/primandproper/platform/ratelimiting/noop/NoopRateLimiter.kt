package com.primandproper.platform.ratelimiting.noop

import com.primandproper.platform.ratelimiting.RateLimiter

/**
 * A no-op [RateLimiter] that never limits: [allow] always returns `true` and [close] does nothing.
 * Port of platform-go's `ratelimiting/noop.rateLimiter` — the safe default for wiring, and the
 * fallback the config factory returns for a blank or `noop` provider.
 */
public class NoopRateLimiter : RateLimiter {
    override suspend fun allow(key: String): Boolean = true

    override fun close() {
    }
}
