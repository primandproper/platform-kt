package com.primandproper.platform.distributedlock.postgres

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of the unit tests in platform-go's `distributedlock/postgres/postgres_test.go`, against a fake client. */
class PostgresLockerTest {
    private class FakeClock(var millis: Long = 0L) {
        fun advance(by: Long) {
            millis += by
        }
    }

    // Passes the first [proceedFor] calls, then rejects — the analog of the Go tests' counter-driven
    // CircuitBreakerMock ("Acquire proceeds, the next call is blocked").
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
        client: FakeAdvisoryLockClient,
        cb: CircuitBreaker = NoopCircuitBreaker,
        clock: FakeClock = FakeClock(),
        obs: Observer = noopObserver("test"),
        namespace: Int = 0,
    ): PostgresLocker = PostgresLocker(obs, client, cb, namespace, 5.seconds) { clock.millis }

    // ---- Acquire ----

    @Test
    fun `Acquire happy path`() =
        runTest {
            val obs = RecordingObserver()
            val fc = FakeAdvisoryLockClient(tryLockResult = true)
            val got = locker(fc, obs = obs).acquire("k", 1.minutes)

            assertEquals("k", got.key)
            assertEquals(1.minutes, got.ttl)
            val op = obs.operations.last { it.name == "Acquire" }
            assertTrue(op.errors.isEmpty())
            assertEquals(hashLockID(0, "k"), op.values["lock.id"])
        }

    @Test
    fun `Acquire rejects empty key`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker(FakeAdvisoryLockClient()).acquire("", 1.minutes) }
            assertTrue(isError(e, ErrEmptyKey))
        }

    @Test
    fun `Acquire rejects zero and negative TTL`() =
        runTest {
            assertTrue(
                isError(
                    assertFailsWith<PlatformException> { locker(FakeAdvisoryLockClient()).acquire("k", Duration.ZERO) },
                    ErrInvalidTTL,
                ),
            )
            assertTrue(
                isError(
                    assertFailsWith<PlatformException> { locker(FakeAdvisoryLockClient()).acquire("k", -(1.seconds)) },
                    ErrInvalidTTL,
                ),
            )
        }

    @Test
    fun `Acquire blocked by circuit breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker(reject = true)
            val e = assertFailsWith<PlatformException> { locker(FakeAdvisoryLockClient(), cb).acquire("k", 1.minutes) }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(1, cb.rejectionCount)
        }

    @Test
    fun `Acquire contention returns ErrLockNotAcquired and frees the conn`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(tryLockResult = false)
            val e = assertFailsWith<PlatformException> { locker(fc, cb).acquire("k", 1.minutes) }
            assertTrue(isError(e, ErrLockNotAcquired))
            assertEquals(1, cb.successCount)
            assertTrue(fc.connections.single().released)
        }

    @Test
    fun `Acquire pool saturation returns ErrLockNotAcquired`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(reserveError = PoolSaturatedException())
            val e = assertFailsWith<PlatformException> { locker(fc, cb).acquire("k", 1.minutes) }
            assertTrue(isError(e, ErrLockNotAcquired))
            assertEquals(1, cb.successCount)
        }

    @Test
    fun `Acquire records the error and trips breaker when reservation fails`() =
        runTest {
            val obs = RecordingObserver()
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(reserveError = RuntimeException("conn boom"))

            assertFailsWith<RuntimeException> { locker(fc, cb, obs = obs).acquire("k", 1.minutes) }
            assertEquals(1, cb.failureCount)
            val op = obs.operations.last { it.name == "Acquire" }
            assertEquals(1, op.errors.size)
        }

    @Test
    fun `Acquire advisory-lock query failure trips breaker and frees the conn`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(tryLockError = RuntimeException("query boom"))
            assertFailsWith<RuntimeException> { locker(fc, cb).acquire("k", 1.minutes) }
            assertEquals(1, cb.failureCount)
            assertTrue(fc.connections.single().released)
        }

    // ---- Release ----

    @Test
    fun `Release happy path returns the conn to the pool`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val h = locker(fc).acquire("k", 1.minutes)
            h.release()
            assertTrue(fc.connections.single().released)
        }

    @Test
    fun `Release with unlock reporting false surfaces an error and discards the conn`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = false)
            val h = locker(fc).acquire("k", 1.minutes)
            assertFailsWith<PlatformException> { h.release() }
            assertTrue(fc.connections.single().discarded)
        }

    @Test
    fun `double release returns ErrLockNotHeld`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val h = locker(fc).acquire("k", 1.minutes)
            h.release()
            val e = assertFailsWith<PlatformException> { h.release() }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `Release blocked by circuit breaker`() =
        runTest {
            val cb = GateBreaker(proceedFor = 1)
            val fc = FakeAdvisoryLockClient(tryLockResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.release() }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(2, cb.calls)
        }

    @Test
    fun `Release SQL failure trips breaker`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockError = RuntimeException("unlock boom"))
            val h = locker(fc, cb).acquire("k", 1.minutes)
            assertFailsWith<RuntimeException> { h.release() }
            assertEquals(1, cb.successCount)
            assertEquals(1, cb.failureCount)
        }

    @Test
    fun `Release after TTL expiry returns ErrLockNotHeld but frees the conn`() =
        runTest {
            val clock = FakeClock()
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val h = locker(fc, clock = clock).acquire("k", 1.minutes)

            clock.advance(2L * 60 * 1000) // past the 1-minute TTL

            val e = assertFailsWith<PlatformException> { h.release() }
            assertTrue(isError(e, ErrLockNotHeld))
            assertEquals(1, fc.connections.single().unlockCalls)
            assertTrue(fc.connections.single().released)
        }

    // ---- Refresh ----

    @Test
    fun `Refresh happy path updates TTL`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, alive = true)
            val h = locker(fc).acquire("k", 1.minutes)
            h.refresh(5.minutes)
            assertEquals(5.minutes, h.ttl)
            assertEquals(1, fc.connections.single().aliveCalls)
        }

    @Test
    fun `Refresh rejects zero TTL and leaves TTL unchanged`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true)
            val h = locker(fc).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(Duration.ZERO) }
            assertTrue(isError(e, ErrInvalidTTL))
            assertEquals(1.minutes, h.ttl)
        }

    @Test
    fun `Refresh after release returns ErrLockNotHeld`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val h = locker(fc).acquire("k", 1.minutes)
            h.release()
            val e = assertFailsWith<PlatformException> { h.refresh(1.minutes) }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `Refresh liveness failure trips breaker and returns ErrLockNotHeld`() =
        runTest {
            val cb = RecordingCircuitBreaker()
            val fc = FakeAdvisoryLockClient(tryLockResult = true, alive = false)
            val h = locker(fc, cb).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(5.minutes) }
            assertTrue(isError(e, ErrLockNotHeld))
            assertEquals(1, cb.failureCount)
            assertEquals(1.minutes, h.ttl)
        }

    @Test
    fun `Refresh after TTL expiry returns ErrLockNotHeld and frees the lock`() =
        runTest {
            val clock = FakeClock()
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val h = locker(fc, clock = clock).acquire("k", 1.minutes)

            clock.advance(2L * 60 * 1000)

            val e = assertFailsWith<PlatformException> { h.refresh(5.minutes) }
            assertTrue(isError(e, ErrLockNotHeld))
            assertEquals(1.minutes, h.ttl) // TTL bookkeeping untouched on a failed refresh
            assertEquals(1, fc.connections.single().unlockCalls)
            // The lock was dropped, so a later release just sees it gone.
            assertTrue(isError(assertFailsWith<PlatformException> { h.release() }, ErrLockNotHeld))
        }

    @Test
    fun `Refresh blocked by circuit breaker`() =
        runTest {
            val cb = GateBreaker(proceedFor = 1)
            val fc = FakeAdvisoryLockClient(tryLockResult = true)
            val h = locker(fc, cb).acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { h.refresh(1.minutes) }
            assertTrue(isError(e, ErrCircuitBroken))
            assertEquals(2, cb.calls)
        }

    // ---- Ping / Close ----

    @Test
    fun `Ping success and error`() =
        runTest {
            val fc = FakeAdvisoryLockClient()
            locker(fc).ping()
            assertEquals(1, fc.pingCalls)

            val bad = FakeAdvisoryLockClient(pingError = RuntimeException("ping boom"))
            assertFailsWith<RuntimeException> { locker(bad).ping() }
        }

    @Test
    fun `Close with no outstanding locks succeeds`() =
        runTest {
            locker(FakeAdvisoryLockClient()).close()
        }

    @Test
    fun `Close releases all outstanding locks`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = true)
            val l = locker(fc)
            l.acquire("a", 1.minutes)
            l.acquire("b", 1.minutes)
            l.close()
            assertEquals(2, fc.connections.size)
            assertTrue(fc.connections.all { it.released })
        }

    @Test
    fun `Close surfaces release errors`() =
        runTest {
            val fc = FakeAdvisoryLockClient(tryLockResult = true, unlockResult = false)
            val l = locker(fc)
            l.acquire("a", 1.minutes)
            assertFailsWith<PlatformException> { l.close() }
        }

    // ---- hashLockID ----

    @Test
    fun `hashLockID is stable and separates namespaces and keys`() {
        assertEquals(hashLockID(0, "k"), hashLockID(0, "k"))
        assertFalse(hashLockID(0, "k") == hashLockID(1, "k"))
        assertFalse(hashLockID(0, "a") == hashLockID(0, "b"))
    }
}
