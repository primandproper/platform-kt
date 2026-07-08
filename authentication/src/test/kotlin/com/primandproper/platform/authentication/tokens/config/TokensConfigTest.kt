package com.primandproper.platform.authentication.tokens.config

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun `provideTokenIssuer builds a JWT issuer`() {
        val issuer = config().provideTokenIssuer()
        checkNotNull(issuer)
    }

    @Test
    fun `provideTokenIssuer rejects invalid base64 key`() {
        assertFailsWith<IllegalArgumentException> { config(key = "not-valid-base64!!!").provideTokenIssuer() }
    }

    @Test
    fun `provideTokenIssuer rejects a wrong-length key`() {
        assertFailsWith<IllegalArgumentException> { config(key = validKey(16)).provideTokenIssuer() }
    }

    @Test
    fun `provideTokenIssuer rejects an unknown provider`() {
        assertFailsWith<IllegalArgumentException> { config(provider = "some-unknown-provider").provideTokenIssuer() }
    }

    @Test
    fun `provideTokenIssuer leaves PASETO a documented seam`() {
        assertFailsWith<IllegalArgumentException> { config(provider = PROVIDER_PASETO).provideTokenIssuer() }
    }
}
