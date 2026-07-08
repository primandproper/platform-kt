package com.primandproper.platform.authentication.tokens.config

import com.primandproper.platform.authentication.tokens.Issuer
import com.primandproper.platform.authentication.tokens.jwt.newJwtSigner
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** Selects JWT as the token backend. Port of platform-go's `tokenscfg.ProviderJWT`. */
public const val PROVIDER_JWT: String = "jwt"

/**
 * Selects PASETO as the token backend. Port of platform-go's `tokenscfg.ProviderPASETO`. PASETO v2
 * local tokens are XChaCha20-Poly1305, which the JDK does not provide — so this port leaves the
 * PASETO backend as a documented `TODO(paseto)` seam and [TokensConfig.provideTokenIssuer] throws for
 * it, exactly as `:cryptography-jvm` leaves `salsa20` a seam.
 */
public const val PROVIDER_PASETO: String = "paseto"

private const val SIGNING_KEY_BYTES = 32

/**
 * Token issuer configuration. Port of platform-go's `tokenscfg.Config`: the chosen [provider], the
 * [issuer]/[audience] the tokens are minted for, the base64url-encoded 32-byte [base64EncodedSigningKey],
 * and the access/refresh token lifetime ceilings.
 */
public data class TokensConfig(
    val provider: String,
    val issuer: String,
    val audience: String,
    val base64EncodedSigningKey: String,
    val maxAccessTokenLifetime: Duration = ZERO,
    val maxRefreshTokenLifetime: Duration = ZERO,
) {
    /**
     * Validates the config, throwing [IllegalArgumentException] on the first problem. Mirrors Go's
     * `ValidateWithContext`: the provider must be one of the known providers and the issuer,
     * audience, and signing key must all be present.
     */
    public fun validate() {
        require(provider.trim().lowercase() in setOf(PROVIDER_JWT, PROVIDER_PASETO)) {
            "provider must be one of \"$PROVIDER_JWT\" or \"$PROVIDER_PASETO\""
        }
        require(issuer.isNotBlank()) { "issuer is required" }
        require(audience.isNotBlank()) { "audience is required" }
        require(base64EncodedSigningKey.isNotBlank()) { "signing key is required" }
    }

    /**
     * Builds the configured [Issuer]. Port of platform-go's `Config.ProvideTokenIssuer`: decodes the
     * base64url signing key (which must be exactly 32 bytes) and selects the backend by provider.
     *
     * @throws IllegalArgumentException when the signing key is not valid base64url, is not 32 bytes,
     *   or the provider is unknown/unsupported.
     */
    public fun provideTokenIssuer(
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
        clock: Clock = Clock.systemUTC(),
    ): Issuer {
        val decodedKey =
            runCatching { Base64.getUrlDecoder().decode(base64EncodedSigningKey) }
                .getOrElse { throw IllegalArgumentException("decoding token signing key: not valid base64url", it) }

        require(decodedKey.size == SIGNING_KEY_BYTES) {
            "token signing key must be $SIGNING_KEY_BYTES bytes, was ${decodedKey.size}"
        }

        return when (provider.trim().lowercase()) {
            PROVIDER_JWT ->
                newJwtSigner(issuer, audience, decodedKey, Observer("jwt_signer", logger, tracerProvider), clock)
            PROVIDER_PASETO ->
                throw IllegalArgumentException("TODO(paseto): PASETO token backend is not yet implemented in this port")
            else ->
                throw IllegalArgumentException("unknown token issuer provider: \"$provider\"")
        }
    }
}
