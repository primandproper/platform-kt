package com.primandproper.platform.authentication.tokens.jwt

import com.primandproper.platform.authentication.tokens.Claims
import com.primandproper.platform.authentication.tokens.InvalidAudienceException
import com.primandproper.platform.authentication.tokens.InvalidIssuerException
import com.primandproper.platform.authentication.tokens.IssuedToken
import com.primandproper.platform.authentication.tokens.Issuer
import com.primandproper.platform.authentication.tokens.ReservedClaimException
import com.primandproper.platform.authentication.tokens.ReservedClaimKeys
import com.primandproper.platform.authentication.tokens.TokenExpiredException
import com.primandproper.platform.authentication.tokens.TokenLifetimeExceededException
import com.primandproper.platform.authentication.tokens.TokenNotYetValidException
import com.primandproper.platform.errors.wrap
import com.primandproper.platform.identifiers.newUuid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

private const val NAME = "jwt_signer"
private const val ALGORITHM = "HS256"
private const val MAC_ALGORITHM = "HmacSHA256"
private val DEFAULT_EXPIRY = 10.minutes

/**
 * Builds an HS256 JWT [Issuer], the port of platform-go's `jwt.NewJWTSigner`.
 *
 * Tokens are standard RFC 7519 JWTs — `base64url(header).base64url(payload).base64url(signature)`,
 * signed with HMAC-SHA256 over the header and payload via `javax.crypto`. The issuer owns the
 * registered claims (`exp`, `nbf`, `iat`, `aud`, `iss`, `sub`, `jti`); callers supply
 * application-specific claims via `extraClaims`, and passing a reserved key throws [ReservedClaimException].
 *
 * @param clock the time source for minting and validating claims; defaults to the system UTC clock.
 *   Tests inject a fixed clock to drive expiry deterministically.
 * @param observer the observability sink; defaults to a no-op.
 * @param maxLifetime a hard ceiling on the requested token lifetime; [issueToken] rejects any request
 *   exceeding it with [TokenLifetimeExceededException]. [ZERO] (the default) disables the ceiling.
 *   [TokensConfig.Issuer] wires this from the configured access/refresh maxima.
 */
public fun newJwtSigner(
    issuer: String,
    audience: String,
    signingKey: ByteArray,
    observer: Observer = noopObserver(NAME),
    clock: Clock = Clock.systemUTC(),
    maxLifetime: Duration = ZERO,
): Issuer = JwtSigner(issuer, audience, signingKey.copyOf(), observer, clock, maxLifetime)

private class JwtSigner(
    private val issuer: String,
    private val audience: String,
    private val signingKey: ByteArray,
    private val o11y: Observer,
    private val clock: Clock,
    private val maxLifetime: Duration,
) : Issuer {
    override suspend fun issueToken(
        subject: String,
        expiry: Duration,
        extraClaims: Map<String, Any?>,
    ): IssuedToken =
        o11y.span(NAME) {
            val effectiveExpiry = if (expiry <= Duration.ZERO) DEFAULT_EXPIRY else expiry
            // The configured lifetime is a hard ceiling: reject rather than clamp so a caller cannot
            // mint a longer-lived token than policy allows and be unaware of it.
            if (maxLifetime > ZERO && effectiveExpiry > maxLifetime) {
                throw wrap(
                    TokenLifetimeExceededException(),
                    "requested lifetime $effectiveExpiry exceeds the configured maximum $maxLifetime",
                )
            }
            val jti = newUuid()

            set(Keys.USER_ID, subject)
            set("token.issuer", issuer)
            set("token.audience", audience)
            set("token.jti", jti)
            set("token.ttl", effectiveExpiry.toString())

            val now = clock.instant()
            val claims =
                linkedMapOf<String, Any?>(
                    "exp" to now.plusSeconds(effectiveExpiry.inWholeSeconds).epochSecond,
                    "nbf" to now.minusSeconds(60).epochSecond,
                    "iat" to now.epochSecond,
                    "aud" to audience,
                    "iss" to issuer,
                    "sub" to subject,
                    "jti" to jti,
                )
            for ((k, v) in extraClaims) {
                if (k in ReservedClaimKeys) {
                    throw wrap(ReservedClaimException(), "reserved claim key \"$k\"")
                }
                claims[k] = v
            }

            IssuedToken(sign(claims), jti)
        }

    override suspend fun parseToken(token: String): Claims =
        o11y.span(NAME) {
            val claims = verifyAndDecode(token)
            JwtClaims(claims)
        }

    private fun sign(claims: Map<String, Any?>): String {
        val header = Base64URL.encode(HEADER_JSON.toByteArray(Charsets.UTF_8))
        val payload = Base64URL.encode(encodeJsonObject(claims).toByteArray(Charsets.UTF_8))
        val signingInput = "$header.$payload"
        val signature = Base64URL.encode(hmac(signingInput.toByteArray(Charsets.US_ASCII)))
        return "$signingInput.$signature"
    }

    private fun verifyAndDecode(token: String): Map<String, Any?> {
        val parts = token.split(".")
        require(parts.size == 3) { "malformed JWT: expected 3 segments" }

        val header = decodeJsonObject(String(Base64URL.decode(parts[0]), Charsets.UTF_8))
        val alg = header["alg"] as? String
        require(alg == ALGORITHM) { "unexpected signing method: $alg" }

        val expectedSignature = hmac("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        val actualSignature = Base64URL.decode(parts[2])
        require(MessageDigest.isEqual(expectedSignature, actualSignature)) { "signature is invalid" }

        val claims = decodeJsonObject(String(Base64URL.decode(parts[1]), Charsets.UTF_8))
        validate(claims)
        return claims
    }

    private fun validate(claims: Map<String, Any?>) {
        val now = clock.instant()

        val exp = claims.claimInstant("exp") ?: throw TokenExpiredException()
        if (now.isAfter(exp)) throw TokenExpiredException()

        claims.claimInstant("nbf")?.let { nbf ->
            if (now.isBefore(nbf)) throw TokenNotYetValidException()
        }

        if (claims["aud"] != audience) throw InvalidAudienceException()
        if (claims["iss"] != issuer) throw InvalidIssuerException()
    }

    private fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey, MAC_ALGORITHM))
        return mac.doFinal(data)
    }

    private companion object {
        // Compact, key-ordered header so the base64url encoding is stable across runs.
        const val HEADER_JSON = """{"alg":"HS256","typ":"JWT"}"""
    }
}

/** Reads a numeric-date claim (epoch seconds) as an [Instant], or `null` if absent/non-numeric. */
private fun Map<String, Any?>.claimInstant(key: String): Instant? = (this[key] as? Number)?.let { Instant.ofEpochSecond(it.toLong()) }

/** Adapts a decoded JWT claim map to [Claims]. Port of platform-go's `jwtClaims`. */
private class JwtClaims(
    private val inner: Map<String, Any?>,
) : Claims {
    override fun subject(): String = inner["sub"] as? String ?: ""

    override fun jti(): String = inner["jti"] as? String ?: ""

    override fun expiresAt(): Instant? = inner.claimInstant("exp")

    override fun get(key: String): Any? = inner[key]

    override fun getStringOrNull(key: String): String? = inner[key] as? String
}

/** base64url without padding, matching the JWT segment encoding. */
private object Base64URL {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray = decoder.decode(text)
}
