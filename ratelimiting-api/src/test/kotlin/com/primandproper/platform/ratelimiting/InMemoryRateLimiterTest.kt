package com.primandproper.platform.ratelimiting

import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Port of platform-go's `ratelimiting_test.go`, plus deterministic refill and concurrency coverage. */
class InMemoryRateLimiterTest {
    // Builds a limiter over a controllable time source so refill is exercised without real sleeps.
    private fun limiter(
        requestsPerSec: Double,
        burstSize: Int,
        timeSource: TestTimeSource = TestTimeSource(),
    ): InMemoryRateLimiter = InMemoryRateLimiter(noopObserver("test"), requestsPerSec, burstSize, timeSource)

    @Test
    fun `allows within burst then denies`() =
        runTest {
            // Fixed clock: no refill happens between calls, so exactly `burst` requests are admitted.
            val rl = limiter(requestsPerSec = 10.0, burstSize = 3)

            assertTrue(rl.allow("key1"))
            assertTrue(rl.allow("key1"))
            assertTrue(rl.allow("key1"))
            assertFalse(rl.allow("key1"))
        }

    @Test
    fun `different keys have independent limits`() =
        runTest {
            val rl = limiter(requestsPerSec = 10.0, burstSize = 1)

            assertTrue(rl.allow("key1"))
            assertTrue(rl.allow("key2"))
            assertFalse(rl.allow("key1"))
            assertFalse(rl.allow("key2"))
        }

    @Test
    fun `bucket refills over injected time`() =
        runTest {
            val clock = TestTimeSource()
            // 1 token/sec, burst 1: one request, then throttled until a full second accrues.
            val rl = limiter(requestsPerSec = 1.0, burstSize = 1, timeSource = clock)

            assertTrue(rl.allow("k"))
            assertFalse(rl.allow("k"))

            clock += 1.seconds // +1s → exactly one token back
            assertTrue(rl.allow("k"))
            assertFalse(rl.allow("k"))
        }

    @Test
    fun `partial refill accrues fractional tokens`() =
        runTest {
            val clock = TestTimeSource()
            // 10 tokens/sec, burst 2: drain the burst, then 100ms accrues exactly one token.
            val rl = limiter(requestsPerSec = 10.0, burstSize = 2, timeSource = clock)

            assertTrue(rl.allow("k"))
            assertTrue(rl.allow("k"))
            assertFalse(rl.allow("k"))

            clock += 100.milliseconds // +100ms → +1 token at 10/s
            assertTrue(rl.allow("k"))
            assertFalse(rl.allow("k"))
        }

    @Test
    fun `non-positive rate never refills past the initial burst`() =
        runTest {
            val clock = TestTimeSource()
            // rate.Limit(0) accrues no tokens: x/time/rate seeds tokens at 0 (not burst) and
            // tokensFromDuration returns 0 for a non-positive limit, so even the initial burst is
            // never granted — every request is denied, now and as time advances.
            val rl = limiter(requestsPerSec = 0.0, burstSize = 2, timeSource = clock)

            assertFalse(rl.allow("k"))
            assertFalse(rl.allow("k"))
            assertFalse(rl.allow("k"))

            clock += 10.seconds // +10s changes nothing when the rate is zero
            assertFalse(rl.allow("k"))
        }

    @Test
    fun `Close is safe`() {
        limiter(requestsPerSec = 10.0, burstSize = 1).close()
    }

    @Test
    fun `Close releases per-key limiters`() =
        runTest {
            val rl = limiter(requestsPerSec = 10.0, burstSize = 1)
            rl.allow("key1")
            rl.allow("key2")
            assertEquals(2, rl.limiters.size)

            rl.close()
            assertEquals(0, rl.limiters.size)
        }

    @Test
    fun `allow observes the key and outcome`() =
        runTest {
            val obs = RecordingObserver()
            val rl = InMemoryRateLimiter(obs, requestsPerSec = 10.0, burstSize = 1, timeSource = TestTimeSource())

            assertTrue(rl.allow("k"))
            assertFalse(rl.allow("k"))

            val ops = obs.operations.filter { it.name == "Allow" }
            assertEquals(2, ops.size)
            assertTrue(ops.all { it.ended && it.errors.isEmpty() })
            assertEquals("k", ops.first().values["name"])
            assertEquals(true, ops.first().values["allowed"])
            assertEquals(false, ops.last().values["allowed"])
        }

    @Test
    fun `concurrent requests admit exactly the burst`() {
        // A fixed clock (no refill) with burst 100: 500 racing requests must yield exactly 100 allows,
        // proving the per-bucket synchronization holds under contention.
        val rl = limiter(requestsPerSec = 1.0, burstSize = 100)
        val allowed = AtomicInteger(0)

        runBlocking {
            coroutineScope {
                (1..500)
                    .map {
                        async(Dispatchers.Default) {
                            if (rl.allow("hot")) allowed.incrementAndGet()
                        }
                    }.awaitAll()
            }
        }

        assertEquals(100, allowed.get())
    }
}
