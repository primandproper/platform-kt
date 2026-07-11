package com.primandproper.platform.distributedlock.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `distributedlock/noop/noop_test.go`. */
class NoopLockerTest {
    @Test
    fun `Acquire returns a usable handle`() =
        runTest {
            val lock = NoopLocker.acquire("k", 1.seconds)
            assertEquals("k", lock.key)
            assertEquals(1.seconds, lock.ttl)
        }

    @Test
    fun `contended acquires both succeed`() =
        runTest {
            val l = NoopLocker
            l.acquire("shared", 1.seconds)
            l.acquire("shared", 1.seconds)
        }

    @Test
    fun `Ping succeeds`() =
        runTest {
            NoopLocker.ping()
        }

    @Test
    fun `Close succeeds`() =
        runTest {
            NoopLocker.close()
        }

    @Test
    fun `Release is a no-op and is idempotent`() =
        runTest {
            val lock = NoopLocker.acquire("k", 1.seconds)
            lock.release()
            lock.release()
        }

    @Test
    fun `Refresh updates the reported ttl`() =
        runTest {
            val lock = NoopLocker.acquire("k", 1.seconds)
            lock.refresh(5.seconds)
            assertEquals(5.seconds, lock.ttl)
        }
}
