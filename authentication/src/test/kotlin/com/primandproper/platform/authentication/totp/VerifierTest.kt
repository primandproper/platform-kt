package com.primandproper.platform.authentication.totp

import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val EXAMPLE_SECRET = "HEREISASECRETWHICHIVEMADEUPBECAUSEIWANNATESTRELIABLY"
private val FIXED_NOW: Instant = Instant.ofEpochSecond(1_700_000_000)

private fun verifierAt(
    now: Instant,
    observer: RecordingObserver = RecordingObserver(),
): Pair<Verifier, RecordingObserver> = newTotpVerifier(observer, Clock.fixed(now, ZoneOffset.UTC)) to observer

class VerifierTest {
    @Test
    fun `valid code verifies`() =
        runTest {
            val (verifier, _) = verifierAt(FIXED_NOW)
            val code = generateTotpCode(EXAMPLE_SECRET, FIXED_NOW)
            verifier.verify(EXAMPLE_SECRET, code) // must not throw
        }

    @Test
    fun `empty code throws ErrCodeRequired`() =
        runTest {
            val (verifier, _) = verifierAt(FIXED_NOW)
            val ex = assertFailsWith<Throwable> { verifier.verify(EXAMPLE_SECRET, "") }
            assertTrue(isError(ex, ErrCodeRequired))
        }

    @Test
    fun `invalid code throws ErrInvalidCode`() =
        runTest {
            val (verifier, _) = verifierAt(FIXED_NOW)
            val ex = assertFailsWith<Throwable> { verifier.verify(EXAMPLE_SECRET, "000000") }
            assertTrue(isError(ex, ErrInvalidCode))
        }

    @Test
    fun `malformed base32 secret throws ErrInvalidCode not IllegalArgumentException`() =
        runTest {
            val (verifier, _) = verifierAt(FIXED_NOW)
            // '1' is not an RFC 4648 base32 character; a bad stored secret is a normal auth failure
            // (→ ErrInvalidCode), not an IllegalArgumentException escaping the verifier.
            val ex = assertFailsWith<Throwable> { verifier.verify("ABC123!", "000000") }
            assertTrue(isError(ex, ErrInvalidCode))
        }

    @Test
    fun `valid code opens and ends an operation with no recorded error`() =
        runTest {
            val (verifier, observer) = verifierAt(FIXED_NOW)
            val code = generateTotpCode(EXAMPLE_SECRET, FIXED_NOW)

            verifier.verify(EXAMPLE_SECRET, code)

            assertEquals(1, observer.operations.size)
            assertTrue(observer.operations[0].ended)
            assertTrue(observer.operations[0].errors.isEmpty())
        }

    @Test
    fun `invalid code still ends the operation cleanly`() =
        runTest {
            val (verifier, observer) = verifierAt(FIXED_NOW)

            assertFailsWith<Throwable> { verifier.verify(EXAMPLE_SECRET, "000000") }

            assertEquals(1, observer.operations.size)
            assertTrue(observer.operations[0].ended)
            // platform-go returns the sentinel without calling op.Error, so nothing is recorded.
            assertTrue(observer.operations[0].errors.isEmpty())
        }
}
