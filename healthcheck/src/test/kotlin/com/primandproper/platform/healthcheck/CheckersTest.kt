package com.primandproper.platform.healthcheck

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of platform-go's `checkers_test.go`. */
class CheckersTest {
    private class MockDbClient(private val ready: Boolean) : DatabaseReadyChecker {
        override suspend fun isReady(): Boolean = ready
    }

    private class MockCacheClient(private val error: Throwable? = null) : CacheReadyChecker {
        override suspend fun ping() {
            error?.let { throw it }
        }
    }

    private class MockMqClient(private val error: Throwable? = null) : MessageQueueReadyChecker {
        override suspend fun ping() {
            error?.let { throw it }
        }
    }

    private companion object {
        val STUB_ERROR = RuntimeException("stub error")
    }

    // --- database checker ---

    @Test
    fun `database checker ready`() =
        runTest {
            val checker = DatabaseChecker("postgres", MockDbClient(ready = true))

            assertEquals("postgres", checker.name)
            checker.check() // no throw
        }

    @Test
    fun `database checker not ready`() =
        runTest {
            val checker = DatabaseChecker("postgres", MockDbClient(ready = false))

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertTrue(thrown is DatabaseNotReadyException)
        }

    @Test
    fun `database checker nil client`() =
        runTest {
            val checker = DatabaseChecker("postgres", null)

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertTrue(thrown.message!!.contains("nil"))
        }

    // --- cache checker ---

    @Test
    fun `cache checker ready`() =
        runTest {
            val checker = CacheChecker("redis", MockCacheClient(error = null))

            assertEquals("redis", checker.name)
            checker.check() // no throw
        }

    @Test
    fun `cache checker not ready`() =
        runTest {
            val checker = CacheChecker("redis", MockCacheClient(error = STUB_ERROR))

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertSame(STUB_ERROR, thrown)
        }

    @Test
    fun `cache checker nil client`() =
        runTest {
            val checker = CacheChecker("redis", null)

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertTrue(thrown.message!!.contains("nil"))
        }

    // --- message queue checker ---

    @Test
    fun `message queue checker ready`() =
        runTest {
            val checker = MessageQueueChecker("redis", MockMqClient(error = null))

            assertEquals("redis", checker.name)
            checker.check() // no throw
        }

    @Test
    fun `message queue checker not ready`() =
        runTest {
            val checker = MessageQueueChecker("redis", MockMqClient(error = STUB_ERROR))

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertSame(STUB_ERROR, thrown)
        }

    @Test
    fun `message queue checker nil client`() =
        runTest {
            val checker = MessageQueueChecker("redis", null)

            val thrown = assertFailsWith<Throwable> { checker.check() }
            assertTrue(thrown.message!!.contains("nil"))
        }
}
