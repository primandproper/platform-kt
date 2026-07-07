package com.primandproper.platform.distributedlock

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `distributedlock/memory/memory_test.go`, with an injected clock. */
class MemoryLockerTest {
    // A mutable fake clock so lease-expiry paths are exercised without real sleeps.
    private class FakeClock(var millis: Long = 0L) {
        fun advance(by: Long) {
            millis += by
        }
    }

    private fun locker(
        clock: FakeClock = FakeClock(),
        observer: RecordingObserver? = null,
    ): MemoryLocker = MemoryLocker(observer ?: noopObserver("test")) { clock.millis }

    @Test
    fun `standard construction yields a usable locker`() =
        runTest {
            val l = MemoryLocker()
            val lock = l.acquire("k", 1.seconds)
            assertEquals("k", lock.key)
            assertEquals(1.seconds, lock.ttl)
        }

    @Test
    fun `Acquire happy path observes key and ttl`() =
        runTest {
            val obs = RecordingObserver()
            val l = locker(observer = obs)

            val lock = l.acquire("k", 1.seconds)
            assertEquals("k", lock.key)
            assertEquals(1.seconds, lock.ttl)

            obs.assertObservedOperationWithValues("lock.key" to "k", "lock.ttl" to 1.seconds)
            val op = obs.operations.last { it.name == "Acquire" }
            assertTrue(op.errors.isEmpty())
        }

    @Test
    fun `Acquire contended returns ErrLockNotAcquired`() =
        runTest {
            val l = locker()
            l.acquire("shared", 1.minutes)
            val e = assertFailsWith<PlatformException> { l.acquire("shared", 1.minutes) }
            assertTrue(isError(e, ErrLockNotAcquired))
        }

    @Test
    fun `re-acquire after expiry succeeds`() =
        runTest {
            val clock = FakeClock()
            val l = locker(clock)
            l.acquire("exp", 50.milliseconds)
            clock.advance(80L)
            // Previously held, now expired: acquiring again must succeed.
            val lock = l.acquire("exp", 1.seconds)
            assertEquals("exp", lock.key)
        }

    @Test
    fun `Acquire rejects empty key but still observes the inputs`() =
        runTest {
            val obs = RecordingObserver()
            val l = locker(observer = obs)

            val e = assertFailsWith<PlatformException> { l.acquire("", 1.seconds) }
            assertTrue(isError(e, ErrEmptyKey))

            obs.assertObservedOperationWithValues("lock.key" to "", "lock.ttl" to 1.seconds)
        }

    @Test
    fun `Acquire rejects zero TTL`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker().acquire("k", 0.seconds) }
            assertTrue(isError(e, ErrInvalidTTL))
        }

    @Test
    fun `Acquire rejects negative TTL`() =
        runTest {
            val e = assertFailsWith<PlatformException> { locker().acquire("k", -(1.seconds)) }
            assertTrue(isError(e, ErrInvalidTTL))
        }

    @Test
    fun `Acquire sweeps expired entries for other keys`() =
        runTest {
            val clock = FakeClock()
            val l = locker(clock)

            l.acquire("keyA", 1.milliseconds)
            clock.advance(10L)

            // Acquiring an unrelated key must sweep keyA's now-expired entry.
            l.acquire("keyB", 1.minutes)

            assertTrue(!l.held.containsKey("keyA"))
            assertEquals(1, l.held.size)
        }

    @Test
    fun `Release happy path`() =
        runTest {
            val l = locker()
            val lock = l.acquire("k", 1.minutes)
            lock.release()
        }

    @Test
    fun `released lock can be reacquired`() =
        runTest {
            val l = locker()
            val first = l.acquire("k", 1.minutes)
            first.release()
            l.acquire("k", 1.minutes)
        }

    @Test
    fun `double release returns ErrLockNotHeld`() =
        runTest {
            val l = locker()
            val lock = l.acquire("k", 1.minutes)
            lock.release()
            val e = assertFailsWith<PlatformException> { lock.release() }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `release after expiration returns ErrLockNotHeld`() =
        runTest {
            val clock = FakeClock()
            val l = locker(clock)
            val lock = l.acquire("k", 50.milliseconds)
            clock.advance(80L)
            val e = assertFailsWith<PlatformException> { lock.release() }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `Refresh extends TTL past the original expiry`() =
        runTest {
            val clock = FakeClock()
            val l = locker(clock)
            val lock = l.acquire("k", 50.milliseconds)
            lock.refresh(5.seconds)
            assertEquals(5.seconds, lock.ttl)

            // Even after the original TTL elapses, the lock is still held.
            clock.advance(80L)
            val e = assertFailsWith<PlatformException> { l.acquire("k", 1.seconds) }
            assertTrue(isError(e, ErrLockNotAcquired))
        }

    @Test
    fun `Refresh after expiration returns ErrLockNotHeld`() =
        runTest {
            val clock = FakeClock()
            val l = locker(clock)
            val lock = l.acquire("k", 50.milliseconds)
            clock.advance(80L)
            val e = assertFailsWith<PlatformException> { lock.refresh(1.seconds) }
            assertTrue(isError(e, ErrLockNotHeld))
        }

    @Test
    fun `Refresh rejects invalid TTL`() =
        runTest {
            val l = locker()
            val lock = l.acquire("k", 1.minutes)
            val e = assertFailsWith<PlatformException> { lock.refresh(0.seconds) }
            assertTrue(isError(e, ErrInvalidTTL))
        }

    @Test
    fun `Ping succeeds`() =
        runTest {
            locker().ping()
        }

    @Test
    fun `Close drops outstanding locks and frees the key`() =
        runTest {
            val l = locker()
            val lock = l.acquire("k", 1.minutes)
            l.close()

            // The previous handle now sees the lock as not-held.
            val e = assertFailsWith<PlatformException> { lock.release() }
            assertTrue(isError(e, ErrLockNotHeld))
            // And the key is acquirable again.
            l.acquire("k", 1.seconds)
        }

    @Test
    fun `only one coroutine wins per key`() =
        runTest {
            val l = locker()
            val results =
                (1..100).map {
                    async { runCatching { l.acquire("racekey", 1.minutes) }.isSuccess }
                }.awaitAll()
            assertEquals(1, results.count { it })
        }
}
