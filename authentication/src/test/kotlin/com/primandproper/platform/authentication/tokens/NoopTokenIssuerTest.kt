package com.primandproper.platform.authentication.tokens

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class NoopTokenIssuerTest {
    @Test
    fun `issueToken returns empty values and no error`() =
        runTest {
            val issuer = newNoopTokenIssuer()
            val issued = issuer.issueToken("subject", 1.minutes)
            assertEquals("", issued.token)
            assertEquals("", issued.jti)
        }

    @Test
    fun `parseToken returns empty claims and no error`() =
        runTest {
            val issuer = newNoopTokenIssuer()
            val claims = issuer.parseToken("anything")

            assertEquals("", claims.subject())
            assertEquals("", claims.jti())
            assertNull(claims.expiresAt())

            assertNull(claims["anything"])
            assertNull(claims.getStringOrNull("anything"))
        }
}
