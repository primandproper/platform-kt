package com.primandproper.platform.distributedlock.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** Exercises the hand-written mock doubles, the analog of platform-go's moq-generated `mock`. */
class LockerMockTest {
    @Test
    fun `LockerMock delegates and records calls`() =
        runTest {
            val handle = LockMock(keyFunc = { "k" }, ttlFunc = { 1.seconds })
            val mock =
                LockerMock(
                    acquireFunc = { _, _ -> handle },
                    pingFunc = { },
                    closeFunc = { },
                )

            assertEquals(handle, mock.acquire("k", 1.seconds))
            mock.ping()
            mock.close()

            assertEquals(listOf("k" to 1.seconds), mock.acquireCalls)
            assertEquals(1, mock.pingCalls)
            assertEquals(1, mock.closeCalls)
        }

    @Test
    fun `LockerMock throws when a func is unset`() =
        runTest {
            assertFailsWith<IllegalStateException> { LockerMock().ping() }
        }

    @Test
    fun `LockMock delegates and records calls`() =
        runTest {
            var refreshed: kotlin.time.Duration? = null
            val lock =
                LockMock(
                    keyFunc = { "k" },
                    ttlFunc = { 1.seconds },
                    releaseFunc = { },
                    refreshFunc = { refreshed = it },
                )

            assertEquals("k", lock.key)
            assertEquals(1.seconds, lock.ttl)
            lock.release()
            lock.refresh(5.seconds)

            assertEquals(5.seconds, refreshed)
            assertEquals(1, lock.keyCalls)
            assertEquals(1, lock.ttlCalls)
            assertEquals(1, lock.releaseCalls)
            assertEquals(listOf(5.seconds), lock.refreshCalls)
        }

    @Test
    fun `LockMock throws when a func is unset`() {
        assertFailsWith<IllegalStateException> { LockMock().key }
    }
}
