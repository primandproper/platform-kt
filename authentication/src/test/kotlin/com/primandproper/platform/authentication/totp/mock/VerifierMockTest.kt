package com.primandproper.platform.authentication.totp.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifierMockTest {
    @Test
    fun `records calls and delegates to the configured func`() =
        runTest {
            var seen: Pair<String, String>? = null
            val mock = VerifierMock(verifyFunc = { secret, code -> seen = secret to code })

            mock.verify("secret", "123456")

            assertEquals("secret" to "123456", seen)
            assertEquals(listOf("secret" to "123456"), mock.verifyCalls)
        }

    @Test
    fun `throws when the func is null`() =
        runTest {
            val mock = VerifierMock()
            assertFailsWith<IllegalStateException> { mock.verify("secret", "123456") }
        }
}
