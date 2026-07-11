package com.primandproper.platform.authentication.tokens.jwt

import com.primandproper.platform.authentication.tokens.InvalidAudienceException
import com.primandproper.platform.authentication.tokens.InvalidIssuerException
import com.primandproper.platform.authentication.tokens.Issuer
import com.primandproper.platform.authentication.tokens.ReservedClaimException
import com.primandproper.platform.authentication.tokens.TokenExpiredException
import com.primandproper.platform.authentication.tokens.TokenLifetimeExceededException
import com.primandproper.platform.authentication.tokens.TokenNotYetValidException
import com.primandproper.platform.errors.isError
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val ISSUER = "platform-test"
private const val AUDIENCE = "platform-audience"
private val SIGNING_KEY = "HEREISA32CHARSECRETWHICHISMADEUP".toByteArray()
private val EXAMPLE_EXPIRY = 10.minutes
private const val SUBJECT = "user_id"

private val FIXED_NOW: Instant = Instant.parse("2026-07-07T00:00:00Z")

private fun signerAt(
    now: Instant,
    issuer: String = ISSUER,
    audience: String = AUDIENCE,
    key: ByteArray = SIGNING_KEY,
): Issuer = newJwtSigner(issuer, audience, key, clock = Clock.fixed(now, ZoneOffset.UTC))

private fun base64UrlSegment(json: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

class JwtSignerTest {
    @Test
    fun `issue then parse round-trips the subject`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val issued = signer.issueToken(SUBJECT, EXAMPLE_EXPIRY)
            assertTrue(issued.token.isNotEmpty())
            assertTrue(issued.jti.isNotEmpty())

            val claims = signer.parseToken(issued.token)
            assertEquals(SUBJECT, claims.subject())
            assertEquals(issued.jti, claims.jti())
            assertTrue(claims.expiresAt() != null)
        }

    @Test
    fun `extra claims round-trip`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val issued =
                signer.issueToken(
                    SUBJECT,
                    EXAMPLE_EXPIRY,
                    mapOf("account_id" to "account_123", "sid" to "session_456"),
                )

            val claims = signer.parseToken(issued.token)
            assertEquals("account_123", claims.getStringOrNull("account_id"))
            assertEquals("session_456", claims.getStringOrNull("sid"))
            assertEquals("account_123", claims["account_id"])
        }

    @Test
    fun `missing optional claim returns empty`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val issued = signer.issueToken(SUBJECT, EXAMPLE_EXPIRY)
            val claims = signer.parseToken(issued.token)

            assertNull(claims.getStringOrNull("sid"))
            assertNull(claims["sid"])
        }

    @Test
    fun `rejects reserved claim key`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val ex =
                assertFailsWith<Throwable> {
                    signer.issueToken(SUBJECT, EXAMPLE_EXPIRY, mapOf("sub" to "attacker_id"))
                }
            assertTrue(isError<ReservedClaimException>(ex))
        }

    @Test
    fun `rejects expired token`() =
        runTest {
            val issued = signerAt(FIXED_NOW).issueToken(SUBJECT, EXAMPLE_EXPIRY)
            // A parser whose clock is past the token's exp must reject it.
            val laterSigner = signerAt(FIXED_NOW.plusSeconds(EXAMPLE_EXPIRY.inWholeSeconds + 60))
            val ex = assertFailsWith<Throwable> { laterSigner.parseToken(issued.token) }
            assertTrue(isError<TokenExpiredException>(ex))
        }

    @Test
    fun `rejects not-yet-valid token`() =
        runTest {
            val issued = signerAt(FIXED_NOW).issueToken(SUBJECT, EXAMPLE_EXPIRY)
            // nbf is now-60s; a parser two minutes in the past sees nbf in the future.
            val earlierSigner = signerAt(FIXED_NOW.minusSeconds(120))
            val ex = assertFailsWith<Throwable> { earlierSigner.parseToken(issued.token) }
            assertTrue(isError<TokenNotYetValidException>(ex))
        }

    @Test
    fun `rejects mismatched audience`() =
        runTest {
            val issued = signerAt(FIXED_NOW, audience = "aud-A").issueToken(SUBJECT, EXAMPLE_EXPIRY)
            val otherAudience = signerAt(FIXED_NOW, audience = "aud-B")
            val ex = assertFailsWith<Throwable> { otherAudience.parseToken(issued.token) }
            assertTrue(isError<InvalidAudienceException>(ex))
        }

    @Test
    fun `rejects mismatched issuer`() =
        runTest {
            val issued = signerAt(FIXED_NOW, issuer = "iss-A").issueToken(SUBJECT, EXAMPLE_EXPIRY)
            val otherIssuer = signerAt(FIXED_NOW, issuer = "iss-B")
            val ex = assertFailsWith<Throwable> { otherIssuer.parseToken(issued.token) }
            assertTrue(isError<InvalidIssuerException>(ex))
        }

    @Test
    fun `rejects a header with a truncated unicode escape as an IllegalArgumentException`() =
        runTest {
            // The header is attacker-controlled and parsed before signature verification. A truncated
            // \u escape must surface as the parser's own IllegalArgumentException, not a raw
            // StringIndexOutOfBoundsException/NumberFormatException leaking out of the JSON decoder.
            val header = base64UrlSegment("""{"alg":"HS256","typ":"JWT","x":"\u1"}""")
            val body = base64UrlSegment("""{"sub":"x"}""")
            val token = "$header.$body.$body"

            assertFailsWith<IllegalArgumentException> { signerAt(FIXED_NOW).parseToken(token) }
        }

    @Test
    fun `rejects a token signed with a different key`() =
        runTest {
            val issued = signerAt(FIXED_NOW).issueToken(SUBJECT, EXAMPLE_EXPIRY)
            val wrongKeySigner = signerAt(FIXED_NOW, key = "ADIFFERENT32CHARSECRETMADEUPHERE".toByteArray())
            assertFailsWith<IllegalArgumentException> { wrongKeySigner.parseToken(issued.token) }
        }

    @Test
    fun `rejects a tampered signature`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val issued = signer.issueToken(SUBJECT, EXAMPLE_EXPIRY)
            // Tamper a character in the MIDDLE of the signature segment. Toggling the final base64url
            // char is unreliable: a 32-byte HS256 signature encodes to 43 chars whose last char carries
            // two unused padding bits, so flipping it can decode to the identical signature. A middle
            // character's bits are all significant, so a one-character change always alters the bytes.
            val (header, payload, signature) = issued.token.split(".")
            val at = signature.length / 2
            val flipped = if (signature[at] == 'A') 'B' else 'A'
            val tamperedSignature = signature.substring(0, at) + flipped + signature.substring(at + 1)
            val tampered = "$header.$payload.$tamperedSignature"
            assertFailsWith<IllegalArgumentException> { signer.parseToken(tampered) }
        }

    @Test
    fun `rejects a requested lifetime exceeding the configured ceiling`() =
        runTest {
            // A signer with a 1-hour ceiling must reject a 2-hour request rather than silently mint it.
            val signer = newJwtSigner(ISSUER, AUDIENCE, SIGNING_KEY, clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC), maxLifetime = 1.hours)
            val ex = assertFailsWith<Throwable> { signer.issueToken(SUBJECT, 2.hours) }
            assertTrue(isError<TokenLifetimeExceededException>(ex))
        }

    @Test
    fun `allows a requested lifetime at or below the configured ceiling`() =
        runTest {
            val signer = newJwtSigner(ISSUER, AUDIENCE, SIGNING_KEY, clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC), maxLifetime = 1.hours)
            val issued = signer.issueToken(SUBJECT, 1.hours)
            assertTrue(issued.token.isNotEmpty())
        }

    @Test
    fun `a zero ceiling leaves lifetimes unbounded`() =
        runTest {
            // maxLifetime defaults to ZERO, which disables the ceiling entirely.
            val signer = signerAt(FIXED_NOW)
            val issued = signer.issueToken(SUBJECT, (24 * 365).hours)
            assertTrue(issued.token.isNotEmpty())
        }

    @Test
    fun `default expiry applies for a non-positive requested expiry`() =
        runTest {
            val signer = signerAt(FIXED_NOW)
            val issued = signer.issueToken(SUBJECT, kotlin.time.Duration.ZERO)
            val claims = signer.parseToken(issued.token)
            // Default 10-minute expiry: exp is 600s after the fixed issue instant.
            assertEquals(FIXED_NOW.plusSeconds(600).epochSecond, claims.expiresAt()?.epochSecond)
            assertFalse(claims.expiresAt() == null)
        }
}
