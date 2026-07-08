package com.primandproper.platform.ratelimiting.redis

import com.primandproper.platform.identifiers.newUuid
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `ratelimiting/redis/redis_test.go`, exercised against an in-memory fake client. */
class RedisRateLimiterTest {
    private fun limiter(
        client: RedisClient,
        requestsPerSec: Double = 10.0,
        burstSize: Int = 20,
        observer: RecordingObserver? = null,
        clock: () -> Long = { 0L },
        newMember: () -> String = ::newUuid,
    ): RedisRateLimiter = RedisRateLimiter(observer ?: noopObserver("test"), client, requestsPerSec, burstSize, clock, newMember)

    @Test
    fun `allowed when the script returns 1`() =
        runTest {
            val client = FakeRedisClient(result = 1L)
            val rl = limiter(client)

            assertTrue(rl.allow("test-key"))

            assertEquals(1, client.evalCalls.size)
            assertEquals(SLIDING_WINDOW_SCRIPT, client.evalCalls.first().script)
            assertEquals(listOf("ratelimit:test-key"), client.evalCalls.first().keys)
        }

    @Test
    fun `rejected when the script returns 0`() =
        runTest {
            val client = FakeRedisClient(result = 0L)
            assertFalse(limiter(client).allow("test-key"))
            assertEquals(1, client.evalCalls.size)
        }

    @Test
    fun `passes burst as the limit and sizes the window without truncating a fractional rate`() =
        runTest {
            val client = FakeRedisClient(result = 1L)
            // 0.5 rps with a burst of 3: the old int64(0.5)=0 limit rejected everything.
            val rl = limiter(client, requestsPerSec = 0.5, burstSize = 3, clock = { 1_000L })

            assertTrue(rl.allow("k"))

            val args = client.evalCalls.single().args
            assertEquals(4, args.size) // now, windowMs, limit, member
            assertEquals(1_000L, args[0]) // now
            assertEquals(6_000L, args[1]) // window == burst/rate = 3/0.5 = 6s
            assertEquals(3L, args[2]) // limit == burstSize, not floored to 0
            assertTrue((args[3] as String).startsWith("1000-")) // member == "<now>-<uuid>"
        }

    @Test
    fun `eval error is recorded on the span and rethrown`() =
        runTest {
            val obs = RecordingObserver()
            val client = FakeRedisClient(failOnEval = true)
            val rl = limiter(client, observer = obs)

            assertFailsWith<RuntimeException> { rl.allow("k") }

            val op = obs.operations.last { it.name == "Allow" }
            assertTrue(op.errors.isNotEmpty())
            assertTrue(op.ended)
        }

    @Test
    fun `emits a unique ZADD member per request`() =
        runTest {
            val client = FakeRedisClient(result = 1L)
            // Fixed clock forces same-millisecond requests: only the uuid suffix keeps members distinct.
            val rl = limiter(client, clock = { 42L })

            val calls = 1000
            repeat(calls) { assertTrue(rl.allow("test-key")) }

            val members = client.evalCalls.map { it.args[3] as String }.toSet()
            assertEquals(calls, members.size)
            assertTrue(members.all { it.startsWith("42-") })
        }

    @Test
    fun `close closes the client`() {
        val client = FakeRedisClient()
        limiter(client).close()
        assertEquals(1, client.closeCalls)
    }

    @Test
    fun `close propagates a client error`() {
        val client = FakeRedisClient().apply { closeError = RuntimeException("close failed") }
        assertFailsWith<RuntimeException> { limiter(client).close() }
        assertEquals(1, client.closeCalls)
    }
}
