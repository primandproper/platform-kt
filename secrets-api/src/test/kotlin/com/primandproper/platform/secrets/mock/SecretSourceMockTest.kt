package com.primandproper.platform.secrets.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecretSourceMockTest {
    @Test
    fun `delegates to func and records calls`() =
        runTest {
            val mock = SecretSourceMock(getSecretFunc = { key -> "value-for-$key" })

            assertEquals("value-for-db", mock.getSecret("db"))
            assertEquals(listOf("db"), mock.getSecretCalls)
        }

    @Test
    fun `unmocked getSecret throws`() =
        runTest {
            val mock = SecretSourceMock()
            assertFailsWith<IllegalStateException> { mock.getSecret("x") }
        }

    @Test
    fun `close records and invokes func`() {
        var closed = false
        val mock = SecretSourceMock(closeFunc = { closed = true })

        mock.close()

        assertEquals(1, mock.closeCalls)
        assertEquals(true, closed)
    }
}
