package com.primandproper.platform.authentication.totp

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// RFC 6238 Appendix B test vectors for the SHA-1 seed ASCII "12345678901234567890", base32-encoded.
private const val RFC_SHA1_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

// A made-up but stable base32 secret, matching platform-go's totp test secret.
private const val EXAMPLE_SECRET = "HEREISASECRETWHICHIVEMADEUPBECAUSEIWANNATESTRELIABLY"

class TotpTest {
    @Test
    fun `matches RFC 6238 SHA1 eight-digit vectors`() {
        val options = TotpOptions(digits = TotpDigits.EIGHT)
        val vectors =
            listOf(
                59L to "94287082",
                1111111109L to "07081804",
                1111111111L to "14050471",
                1234567890L to "89005924",
                2000000000L to "69279037",
                20000000000L to "65353130",
            )
        for ((seconds, expected) in vectors) {
            val code = generateTotpCode(RFC_SHA1_SECRET, Instant.ofEpochSecond(seconds), options)
            assertEquals(expected, code, "vector at T=$seconds")
        }
    }

    @Test
    fun `default codes are six digits`() {
        val code = generateTotpCode(EXAMPLE_SECRET, Instant.ofEpochSecond(0))
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `a freshly generated code validates at the same instant`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val code = generateTotpCode(EXAMPLE_SECRET, now)
        assertTrue(validateTotpCode(code, EXAMPLE_SECRET, now))
    }

    @Test
    fun `a wrong code does not validate`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        assertFalse(validateTotpCode("000000", EXAMPLE_SECRET, now))
    }

    @Test
    fun `a code from the previous window still validates within the default skew`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val previousWindow = now.minusSeconds(30)
        val code = generateTotpCode(EXAMPLE_SECRET, previousWindow)
        assertTrue(validateTotpCode(code, EXAMPLE_SECRET, now))
    }

    @Test
    fun `a code two windows in the past is rejected`() {
        val now = Instant.ofEpochSecond(1_700_000_000)
        val twoWindowsAgo = now.minusSeconds(90)
        val code = generateTotpCode(EXAMPLE_SECRET, twoWindowsAgo)
        assertFalse(validateTotpCode(code, EXAMPLE_SECRET, now))
    }
}
