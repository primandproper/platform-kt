package com.primandproper.platform.authentication.tokens

import java.time.Instant
import kotlin.time.Duration

/**
 * A no-op [Issuer]: [issueToken] returns empty values and [parseToken] returns empty [Claims], both
 * without error. Port of platform-go's `tokens.NewNoopTokenIssuer` — a safe default for wiring and
 * for tests that don't exercise real signing.
 */
public fun newNoopTokenIssuer(): Issuer = NoopTokenIssuer

private object NoopTokenIssuer : Issuer {
    override suspend fun issueToken(
        subject: String,
        expiry: Duration,
        extraClaims: Map<String, Any?>,
    ): IssuedToken = IssuedToken("", "")

    override suspend fun parseToken(token: String): Claims = NoopClaims
}

/** An empty [Claims] implementation. Port of platform-go's `tokens.noopClaims`. */
private object NoopClaims : Claims {
    override fun subject(): String = ""

    override fun jti(): String = ""

    override fun expiresAt(): Instant? = null

    override fun get(key: String): Pair<Any?, Boolean> = null to false

    override fun getString(key: String): Pair<String, Boolean> = "" to false
}
