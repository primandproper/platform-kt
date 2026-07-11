package com.primandproper.platform.ratelimiting.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Port of platform-go's `ratelimiting/noop/noop_test.go`. */
class NoopRateLimiterTest {
    @Test
    fun `allow always returns true`() =
        runTest {
            val rl = NoopRateLimiter
            repeat(100) {
                assertTrue(rl.allow("any"))
            }
        }

    @Test
    fun `close is a no-op`() {
        NoopRateLimiter.close()
    }
}
