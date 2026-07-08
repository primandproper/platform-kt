package com.primandproper.platform.authentication.tokens.mock

import com.primandproper.platform.authentication.tokens.IssuedToken
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class IssuerMockTest {
    @Test
    fun `records calls and delegates to the configured func`() =
        runTest {
            val mock = IssuerMock(issueTokenFunc = { subject, _, _ -> IssuedToken("tok-$subject", "jti-1") })

            val issued = mock.issueToken("alice", 5.minutes, mapOf("k" to "v"))

            assertEquals("tok-alice", issued.token)
            assertEquals(1, mock.issueTokenCalls.size)
            assertEquals("alice", mock.issueTokenCalls[0].first)
        }

    @Test
    fun `throws when the func is null`() =
        runTest {
            val mock = IssuerMock()
            assertFailsWith<IllegalStateException> { mock.parseToken("x") }
        }

    @Test
    fun `claims mock records and delegates`() {
        val mock =
            ClaimsMock(
                subjectFunc = { "subject-1" },
                expiresAtFunc = { Instant.EPOCH },
            )
        assertEquals("subject-1", mock.subject())
        assertEquals(Instant.EPOCH, mock.expiresAt())
        assertEquals(1, mock.subjectCalls.size)
    }
}
