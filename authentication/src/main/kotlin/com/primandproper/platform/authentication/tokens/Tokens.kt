package com.primandproper.platform.authentication.tokens

import com.primandproper.platform.errors.PlatformException
import java.time.Instant

/*
 * Token error types. In platform-go these are `var Err… = errors.New(…)` sentinels matched via
 * errors.Is; here they are exception CLASSES thrown fresh at each site and matched by type.
 */

/**
 * Thrown when a caller passed a registered-claim key in `extraClaims`. Reserved claim keys
 * (`iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`) are owned by the issuer and cannot be overridden
 * by callers. Port of platform-go's `tokens.ErrReservedClaim`.
 */
public class ReservedClaimException : PlatformException("reserved claim key in extraClaims")

/**
 * Thrown by [Issuer.parseToken] implementations when a token decodes and authenticates but fails claim
 * validation because it has expired. Port of platform-go's `tokens.ErrTokenExpired`.
 */
public class TokenExpiredException : PlatformException("token is expired")

/** Thrown when the token's `nbf` claim is in the future. Port of platform-go's `tokens.ErrTokenNotYetValid`. */
public class TokenNotYetValidException : PlatformException("token is not yet valid")

/**
 * Thrown by [Issuer.issueToken] when the requested lifetime exceeds the issuer's configured ceiling
 * (`TokensConfig.maxAccessTokenLifetime` / `maxRefreshTokenLifetime`). The ceilings are hard maxima,
 * so an over-long request is rejected rather than silently clamped — a caller must not be able to
 * mint an arbitrarily long-lived token.
 */
public class TokenLifetimeExceededException : PlatformException("requested token lifetime exceeds the configured maximum")

/** Thrown when the token's `aud` claim does not match the issuer's audience. Port of `tokens.ErrInvalidAudience`. */
public class InvalidAudienceException : PlatformException("token audience is not valid")

/** Thrown when the token's `iss` claim does not match the issuer. Port of `tokens.ErrInvalidIssuer`. */
public class InvalidIssuerException : PlatformException("token issuer is not valid")

/**
 * The set of JWT registered claim names (RFC 7519) the issuer owns. Callers MUST NOT include these in
 * `extraClaims` passed to [Issuer.issueToken]. Port of platform-go's `tokens.ReservedClaimKeys`.
 */
public val ReservedClaimKeys: Set<String> = setOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")

/**
 * The parsed claim set from a token. Implementations expose issuer-owned registered claims via typed
 * accessors ([subject], [jti], [expiresAt]) and any application-specific claims via [get] /
 * [getStringOrNull]. Port of platform-go's `tokens.Claims`.
 *
 * Callers that need claims not surfaced by the typed accessors look them up by name, e.g.
 * `claims.getStringOrNull("account_id")`.
 */
public interface Claims {
    /** The `sub` claim, or the empty string if unset. */
    public fun subject(): String

    /** The `jti` claim, or the empty string if unset. */
    public fun jti(): String

    /** The `exp` claim as a UTC instant, or `null` if unset. Mirrors Go's zero-`time.Time`. */
    public fun expiresAt(): Instant?

    /** The raw value for [key], or `null` if the claim is absent. */
    public operator fun get(key: String): Any?

    /** The string value for [key], or `null` if the claim is absent or is not a string. */
    public fun getStringOrNull(key: String): String?
}

/**
 * Issues and parses authentication tokens. Implementations own the standard registered claims
 * (`sub`, `jti`, `iat`, `nbf`, `exp`, `aud`, `iss`); callers supply any application-specific claims
 * via [extraClaims] and read them back through the [Claims] returned by [parseToken]. Port of
 * platform-go's `tokens.Issuer`, with the threaded `context.Context` replaced by suspension.
 */
public interface Issuer {
    /**
     * Issues a token for [subject] valid for [expiry], carrying [extraClaims]. Returns the encoded
     * token string and the generated `jti`. Passing a reserved-claim key in [extraClaims] throws
     * [ReservedClaimException].
     */
    public suspend fun issueToken(
        subject: String,
        expiry: kotlin.time.Duration,
        extraClaims: Map<String, Any?> = emptyMap(),
    ): IssuedToken

    /** Parses and verifies [token], returning its claims, or throwing on any validation failure. */
    public suspend fun parseToken(token: String): Claims
}

/** The pair returned by [Issuer.issueToken]: the encoded [token] string and its generated [jti]. */
public data class IssuedToken(
    val token: String,
    val jti: String,
)
