package com.primandproper.platform.distributedlock.redis

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitState
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.distributedlock.ErrEmptyKey
import com.primandproper.platform.distributedlock.ErrInvalidTTL
import com.primandproper.platform.distributedlock.ErrLockNotAcquired
import com.primandproper.platform.distributedlock.ErrLockNotHeld
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Port of the unit tests in platform-go's `distributedlock/redis/redis_test.go`, against a fake client. */
class RedisLockerTest {
    // A breaker that passes the first [proceedFor] calls, then rejects with ErrCircuitBroken — the
    // Kotlin analog of the Go tests' counter-driven CircuitBreakerMock ("Acquire proceeds, the next
    // call is blocked"). RecordingCircuitBreaker's reject flag is all-or-nothing, so this fills the gap.
    private class GateBreaker(private val proceedFor: Int) : CircuitBreaker {
        var calls: Int = 0
        override val state: StateFlow<CircuitState> = MutableStateFlow(CircuitState.CLOSED).asStateFlow()

        override suspend fun <T> execute(block: suspend () -> T): T {
            calls++
            if (calls > proceedFor) throw ErrCircuitBroken
            return block()
        }
    }

    private fun locker(
        client: FakeRedisLockClient,
        cb: CircuitBreaker = NoopCircuitBreaker,
        obs: Observer = noopObserver("test"),
    ): RedisLocker = RedisLocker(obs, client, cb, "lock:")

    // ---- NewRedisLocker / construction ----

    @Test
    fun `empty addresses is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RedisLocker(RedisLockConfig(addresses = emptyList(), keyPrefix = "lock:"))
        }
    }

    @Test
    fun `standard happy path builds and closes without a live server`() =
        runTest {
            val l = RedisLocker(RedisLockConfig(addresses = listOf("localhost:0"), keyPrefix = "lock:"))
            l.close()
        }

    // ---- Acquire ----

    @Test
    fun `Acquire happy path`() =
        runTest {
            val obs = RecordingObserver()
            val fc = FakeRedisLockClient(setNxResult = true)
            val got = locker(fc, obs = obs).acquire("k", 1.minutes)

            assertEquals("k", got.key)
            assertEquals(1.minutes, got.ttl)
            assertEquals("lock:k", fc.lastSetKey)
            assertEquals(1.minutes.inWholeMilliseconds, fc.lastSetTtl)

            val op = obs.operations.last { it.name == "Acquire" }
            assertTrue(op.errors.isEmpty())
        }

    @Test
    fun `Acquire rejects empty key`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker(FakeRedisLockClient()).acquire("", 1.minutes) }
            assertTrue(isError(e, ErrEmptyKey))
        }

    @Test
    fun `Acquire rejects zero TTL`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker(FakeRedisLockClient()).acquire("k", Duration.ZERO) }
            assertTrue(isError(e, ErrInvalidTTL))
        }

    @Test
    fun `Acquire rejects negative TTL`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker(FakeRedisLockClient()).acquire("k", -(1.minutes)) }
            assertTrue(isError(e, ErrInvalidTTL))
        }

    @Test
    fun `Acquire blocked by circuit breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker(reject = true)
            val e = assertFailsWith<PlatformException> { locker(FakeRedisLockClient(), cb).acquire("k", 1.minutes) }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(1, cb.rejectionCount)
        }

    @Test
    fun `Acquire SetNX backend error trips breaker and records the error`() =
        runTest {
            val obs = RecordingObserver()
            val cb = RecordingCircuitBreaker()
            val fc = FakeRedisLockClient(setNxErr = RuntimeException("redis down"))

            assertFailsWith<RuntimeException> { locker(fc, cb, obs).acquire("k", 1.minutes) }
            assertEquals(1, cb.failureCount)

            val op = obs.operations.last { it.name == "Acquire" }
            assertEquals(1, op.errors.size)
        }

    @Test
    fun `Acquire contention does not fail the breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeRedisLockClient(setNxResult = false)
            val e = assertFailsWith<PlatformException> { locker(fc, cb).acquire("k", 1.minutes) }
            assertTrue(isError(e, ErrLockNotAcquired))
            assertEquals(1, cb.successCount)
            assertEquals(0, cb.failureCount)
        }

    // ---- Release ----

    @Test
    fun `Release happy path`() =
        runTest {
            val fc = FakeRedisLockClient(setNxResult = true, evalResult = 1)
            val l = locker(fc)
            val h = l.acquire("k", 1.minutes)
            h.release()
            assertEquals("lock:k", fc.lastEvalKey)
        }

    @Test
    fun `Release eval reporting not held returns ErrLockNotHeld`() =
        runTest {
            val fc = FakeRedisLockClient(setNxResult = true, evalResult = 0)
            val h = locker(fc).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.release() }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `Release eval backend error trips breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeRedisLockClient(setNxResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)

            fc.evalErr = RuntimeException("eval boom")
            assertFailsWith<RuntimeException> { h.release() }
            assertEquals(2, cb.executeCount)
            assertEquals(1, cb.successCount)
            assertEquals(1, cb.failureCount)
        }

    @Test
    fun `Release blocked by circuit breaker`() =
        runTest {
            val cb = GateBreaker(proceedFor = 1)
            val fc = FakeRedisLockClient(setNxResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.release() }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(2, cb.calls)
        }

    // ---- Refresh ----

    @Test
    fun `Refresh happy path updates TTL`() =
        runTest {
            val fc = FakeRedisLockClient(setNxResult = true, evalResult = 1)
            val h = locker(fc).acquire("k", 1.minutes)
            h.refresh(5.minutes)
            assertEquals(5.minutes, h.ttl)
            assertEquals(5.minutes.inWholeMilliseconds.toString(), fc.lastEvalArgs.last())
        }

    @Test
    fun `Refresh rejects zero TTL and leaves TTL unchanged`() =
        runTest {
            val fc = FakeRedisLockClient(setNxResult = true)
            val h = locker(fc).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(Duration.ZERO) }
            assertTrue(isError(e, ErrInvalidTTL))
            assertEquals(1.minutes, h.ttl)
        }

    @Test
    fun `Refresh eval reporting not held returns ErrLockNotHeld`() =
        runTest {
            val fc = FakeRedisLockClient(setNxResult = true, evalResult = 0)
            val h = locker(fc).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(2.minutes) }
            assertTrue(isError(e, ErrLockNotHeld))
            assertEquals(1.minutes, h.ttl)
        }

    @Test
    fun `Refresh eval backend error trips breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeRedisLockClient(setNxResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)

            fc.evalErr = RuntimeException("eval boom")
            assertFailsWith<RuntimeException> { h.refresh(5.minutes) }
            assertEquals(2, cb.executeCount)
            assertEquals(1, cb.successCount)
            assertEquals(1, cb.failureCount)
        }

    @Test
    fun `Refresh blocked by circuit breaker`() =
        runTest {
            val cb = GateBreaker(proceedFor = 1)
            val fc = FakeRedisLockClient(setNxResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(1.minutes) }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(2, cb.calls)
        }

    // ---- Ping / Close ----

    @Test
    fun `Ping success and error`() =
        runTest {
            val fc = FakeRedisLockClient()
            locker(fc).ping()
            assertEquals(1, fc.pingCalls)

            val bad = FakeRedisLockClient(pingErr = RuntimeException("ping boom"))
            assertFailsWith<RuntimeException> { locker(bad).ping() }
        }

    @Test
    fun `Close success and error`() =
        runTest {
            val fc = FakeRedisLockClient()
            locker(fc).close()
            assertEquals(1, fc.closeCalls)

            val bad = FakeRedisLockClient(closeErr = RuntimeException("close boom"))
            assertFailsWith<RuntimeException> { locker(bad).close() }
        }
}
