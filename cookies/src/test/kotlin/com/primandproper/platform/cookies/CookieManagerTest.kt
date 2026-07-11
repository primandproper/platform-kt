package com.primandproper.platform.cookies

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.observability.testing.observedKeyValue
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Port of platform-go's `cookies/cookies_test.go`. */
class CookieManagerTest {
    private companion object {
        // The same 32-byte secret platform-go's test uses, standard-base64-encoded for both keys.
        const val TEST_KEY = "HEREISA32CHARSECRETWHICHISMADEUP"
        val ENCODED_KEY: String = Base64.getEncoder().encodeToString(TEST_KEY.toByteArray())
    }

    private fun baseConfig() =
        CookieConfig(
            cookieName = "platform_cookie",
            base64EncodedHashKey = ENCODED_KEY,
            base64EncodedBlockKey = ENCODED_KEY,
        )

    // ---- construction --------------------------------------------------------------------------

    @Test
    fun `builds a working manager from a standard config`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            assertEquals("v", manager.decode("n", manager.encode("n", "v")))
        }

    @Test
    fun `rejects an invalid config`() {
        // SameSite=None without SecureOnly is rejected by validation, which the factory runs first.
        assertFailsWith<IllegalArgumentException> {
            newCookieManager(baseConfig().copy(sameSite = SameSite.NONE.value, secureOnly = false))
        }
    }

    @Test
    fun `rejects an invalid hash key without echoing it`() {
        val badKey = "not-valid-base64!!!"
        val ex =
            assertFailsWith<IllegalArgumentException> {
                newCookieManager(baseConfig().copy(base64EncodedHashKey = badKey))
            }
        assertTrue(ex.message?.contains(badKey) != true, "error must not echo the key material")
    }

    @Test
    fun `rejects an invalid block key without echoing it`() {
        val badKey = "not-valid-base64!!!"
        val ex =
            assertFailsWith<IllegalArgumentException> {
                newCookieManager(baseConfig().copy(base64EncodedBlockKey = badKey))
            }
        assertTrue(ex.message?.contains(badKey) != true, "error must not echo the key material")
    }

    // ---- encode / decode -----------------------------------------------------------------------

    @Test
    fun `encode produces a non-empty value and observes one operation`() =
        runTest {
            val obs = RecordingObserver()
            val manager = newCookieManager(baseConfig(), obs)

            val encoded = manager.encode("test", "the-payload")

            assertTrue(encoded.isNotEmpty())
            // The sealed form is not the plaintext.
            assertTrue(encoded != "the-payload")

            // Encode opens (and ends) exactly one observed operation — the internal sealer is noop.
            assertEquals(1, obs.operations.size)
            assertTrue(obs.operations[0].ended)
            obs.assertObservedOperationWithValues(Keys.NAME to "test")
        }

    @Test
    fun `encode of the same value yields different ciphertexts`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            val a = manager.encode("test", "payload")
            val b = manager.encode("test", "payload")
            // Fresh random GCM nonce per seal.
            assertTrue(a != b)
        }

    @Test
    fun `decode round-trips an encoded value and observes both operations`() =
        runTest {
            val obs = RecordingObserver()
            val manager = newCookieManager(baseConfig(), obs)

            val encoded = manager.encode("test", "round-trip")
            val decoded = manager.decode("test", encoded)

            assertEquals("round-trip", decoded)

            assertEquals(2, obs.operations.size)
            assertTrue(obs.operations.all { it.ended })
            obs.operations.forEach { it.assertObserved(observedKeyValue(Keys.NAME, "test")) }
        }

    @Test
    fun `decode round-trips a value containing the delimiter`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            val value = "a|b|c|d"
            assertEquals(value, manager.decode("test", manager.encode("test", value)))
        }

    @Test
    fun `decode rejects a garbage value`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            assertFailsWith<CookieException> {
                manager.decode("test", "this-is-not-a-valid-cookie")
            }
        }

    @Test
    fun `decode rejects a tampered value`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            val encoded = manager.encode("session", "sensitive")

            // Flip a byte in the sealed ciphertext; GCM's tag must reject it.
            val raw = Base64.getUrlDecoder().decode(encoded)
            raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
            val tampered = Base64.getUrlEncoder().encodeToString(raw)

            assertFailsWith<CookieException> { manager.decode("session", tampered) }
        }

    @Test
    fun `decode rejects a value sealed under a different name`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            val encoded = manager.encode("session", "sensitive")
            // Name is bound into the sealed payload, so a cross-name replay is rejected.
            assertFailsWith<CookieException> { manager.decode("other", encoded) }
        }

    @Test
    fun `decode rejects a value past its lifetime`() =
        runTest {
            var clock = Instant.parse("2026-07-07T00:00:00Z")
            val manager = buildCookieManager(baseConfig().copy(lifetime = 30.minutes), noop()) { clock }

            val encoded = manager.encode("session", "sensitive")
            // Round-trips within the lifetime.
            assertEquals("sensitive", manager.decode("session", encoded))

            // Advance past the lifetime; the bound timestamp is now too old.
            clock = clock.plusSeconds(31 * 60)
            assertFailsWith<CookieException> { manager.decode("session", encoded) }
        }

    @Test
    fun `caps a session-cookie value at the 30-day default when lifetime is zero`() =
        runTest {
            var clock = Instant.parse("2026-07-07T00:00:00Z")
            // lifetime = ZERO is a valid session-cookie config (no Max-Age), but the sealed value must
            // still be crypto-bounded at gorilla/securecookie's 30-day default rather than never expiring.
            val manager = buildCookieManager(baseConfig().copy(lifetime = Duration.ZERO), noop()) { clock }

            val encoded = manager.encode("session", "sensitive")

            // Still decodes just under the 30-day cap.
            clock = clock.plusSeconds(29L * 24 * 60 * 60)
            assertEquals("sensitive", manager.decode("session", encoded))

            // Rejected once past the 30-day cap (t0 + 31 days). Before the fix a lifetime==0 value never expired.
            clock = clock.plusSeconds(2L * 24 * 60 * 60)
            assertFailsWith<CookieException> { manager.decode("session", encoded) }
        }

    // ---- buildCookie ---------------------------------------------------------------------------

    @Test
    fun `buildCookie applies configured security attributes and round-trips`() =
        runTest {
            val manager =
                newCookieManager(
                    baseConfig().copy(domain = "example.com", lifetime = 1.hours, secureOnly = true),
                )

            val cookie = manager.buildCookie("session", "the-value")

            assertEquals("session", cookie.name)
            assertTrue(cookie.value.isNotEmpty())
            assertEquals("/", cookie.path)
            assertEquals("example.com", cookie.domain)
            assertTrue(cookie.httpOnly)
            assertTrue(cookie.secure)
            assertEquals(SameSite.LAX, cookie.sameSite)
            assertEquals(3600, cookie.maxAge)
            assertTrue(cookie.expires != null)

            // The embedded value still round-trips through decode.
            assertEquals("the-value", manager.decode("session", cookie.value))
        }

    @Test
    fun `buildCookie honors a configured SameSite policy`() =
        runTest {
            val manager = newCookieManager(baseConfig().copy(sameSite = SameSite.STRICT.value))
            val cookie = manager.buildCookie("session", "v")
            assertEquals(SameSite.STRICT, cookie.sameSite)
        }

    @Test
    fun `buildCookie without a lifetime omits max age and stays non-secure`() =
        runTest {
            val manager = newCookieManager(baseConfig())
            val cookie = manager.buildCookie("session", "v")

            assertNull(cookie.maxAge)
            assertNull(cookie.expires)
            assertTrue(!cookie.secure)
            // HttpOnly and SameSite are non-negotiable defaults regardless of config.
            assertTrue(cookie.httpOnly)
            assertEquals(SameSite.LAX, cookie.sameSite)
        }

    // ---- serialization -------------------------------------------------------------------------

    @Test
    fun `serialize renders the security attributes in header form`() {
        val cookie =
            Cookie(
                name = "session",
                value = "abc",
                domain = "example.com",
                secure = true,
                sameSite = SameSite.STRICT,
                maxAge = 3600,
            )

        val header = cookie.serialize()
        assertEquals(
            "session=abc; Path=/; Domain=example.com; Max-Age=3600; HttpOnly; Secure; SameSite=Strict",
            header,
        )
    }

    @Test
    fun `serialize of a session cookie omits domain max-age and secure`() {
        val header = Cookie(name = "s", value = "v").serialize()
        assertEquals("s=v; Path=/; HttpOnly; SameSite=Lax", header)
    }

    @Test
    fun `serialize zero-pads a single-digit day in the Expires date`() {
        // The 5th must render as "05", not "5" — RFC_1123_DATE_TIME's variable-width day trips strict
        // RFC 7231 (IMF-fixdate) parsers on the 1st through 9th.
        val cookie =
            Cookie(name = "s", value = "v", expires = Instant.parse("2026-03-05T08:07:06Z"))
        val header = cookie.serialize()
        assertTrue(
            header.contains("Expires=Thu, 05 Mar 2026 08:07:06 GMT"),
            "expected a zero-padded IMF-fixdate, got: $header",
        )
    }

    @Test
    fun `serialize omits Max-Age when it is zero matching Go`() {
        // Go's http.Cookie treats MaxAge == 0 as "unset" and omits the attribute; only a negative
        // value emits Max-Age=0 to delete the cookie now.
        val header = Cookie(name = "s", value = "v", maxAge = 0).serialize()
        assertTrue(!header.contains("Max-Age"), "Max-Age=0 must be omitted, got: $header")
        assertEquals("s=v; Path=/; HttpOnly; SameSite=Lax", header)
    }

    @Test
    fun `serialize emits Max-Age=0 for a negative max age to delete now`() {
        val header = Cookie(name = "s", value = "v", maxAge = -1).serialize()
        assertTrue(header.contains("; Max-Age=0"), "a negative max age must delete now, got: $header")
    }

    private fun noop() = RecordingObserver()
}
