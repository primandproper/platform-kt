package com.primandproper.platform.authentication.tokens.config

import com.primandproper.platform.authentication.tokens.TokenLifetimeExceededException
import com.primandproper.platform.errors.isError
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// Mirrors platform-go's base64.URLEncoding (padded), which is what real token configs carry.
private fun validKey(byteCount: Int = 32): String = Base64.getUrlEncoder().encodeToString(ByteArray(byteCount) { it.toByte() })

private fun config(
    provider: String = PROVIDER_JWT,
    issuer: String = "issuer",
    audience: String = "audience",
    key: String = validKey(),
): TokensConfig = TokensConfig(provider, issuer, audience, key)

class TokensConfigTest {
    @Test
    fun `validate accepts a well-formed config`() {
        config().validate()
    }

    @Test
    fun `validate rejects a missing key`() {
        assertFailsWith<IllegalArgumentException> { config(key = "").validate() }
    }

    @Test
    fun `validate rejects an invalid provider`() {
        assertFailsWith<IllegalArgumentException> { config(provider = "not-a-real-provider").validate() }
    }

    @Test
    fun `validate rejects an empty provider`() {
        assertFailsWith<IllegalArgumentException> { config(provider = "").validate() }
    }

    @Test
    fun `validate rejects a missing issuer`() {
        assertFailsWith<IllegalArgumentException> { config(issuer = "").validate() }
    }

    @Test
    fun `Issuer builds a JWT issuer`() {
        val issuer = config().Issuer()
        checkNotNull(issuer)
    }

    @Test
    fun `Issuer rejects invalid base64 key`() {
        assertFailsWith<IllegalArgumentException> { config(key = "not-valid-base64!!!").Issuer() }
    }

    @Test
    fun `Issuer rejects a wrong-length key`() {
        assertFailsWith<IllegalArgumentException> { config(key = validKey(16)).Issuer() }
    }

    @Test
    fun `Issuer rejects an unknown provider`() {
        assertFailsWith<IllegalArgumentException> { config(provider = "some-unknown-provider").Issuer() }
    }

    @Test
    fun `Issuer leaves PASETO a documented seam`() {
        assertFailsWith<IllegalArgumentException> { config(provider = PROVIDER_PASETO).Issuer() }
    }

    @Test
    fun `Issuer enforces the configured lifetime ceiling`() =
        runTest {
            // The configured maxima are hard ceilings; the built issuer must reject an over-long request.
            val cfg =
                config().copy(maxAccessTokenLifetime = 15.minutes, maxRefreshTokenLifetime = 24.hours)
            val issuer = cfg.Issuer()
            val ex = assertFailsWith<Throwable> { issuer.issueToken("subject", 48.hours) }
            assertTrue(isError<TokenLifetimeExceededException>(ex))
        }
}
