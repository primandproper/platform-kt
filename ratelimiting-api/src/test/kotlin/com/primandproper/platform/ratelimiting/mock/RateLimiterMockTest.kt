package com.primandproper.platform.ratelimiting.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterMockTest {
    @Test
    fun `allow delegates to allowFunc and records the call`() =
        runTest {
            val mock = RateLimiterMock(allowFunc = { key -> key != "blocked" })

            assertTrue(mock.allow("ok"))
            assertFalse(mock.allow("blocked"))
            assertEquals(listOf("ok", "blocked"), mock.allowCalls)
        }

    @Test
    fun `allow with a null allowFunc throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { RateLimiterMock().allow("x") }
        }

    @Test
    fun `close records the call and invokes closeFunc`() {
        var closed = false
        val mock = RateLimiterMock(closeFunc = { closed = true })

        mock.close()
        mock.close()

        assertTrue(closed)
        assertEquals(2, mock.closeCalls)
    }

    @Test
    fun `close with a null closeFunc is a no-op`() {
        val mock = RateLimiterMock()
        mock.close()
        assertEquals(1, mock.closeCalls)
    }
}
