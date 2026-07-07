package com.primandproper.platform.authentication.totp

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.newError
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import java.time.Clock

private const val NAME = "totp"

/** The provided TOTP code did not validate against the secret. Port of platform-go's `totp.ErrInvalidCode`. */
public val ErrInvalidCode: PlatformException = newError("invalid TOTP code")

/** TOTP is enabled but no code was provided. Port of platform-go's `totp.ErrCodeRequired`. */
public val ErrCodeRequired: PlatformException = newError("TOTP code required but not provided")

/**
 * Verifies a TOTP code against a shared secret. Port of platform-go's `totp.Verifier`. It is
 * intentionally decoupled from `authentication.Authenticator` so that password verification and
 * second-factor verification can evolve independently.
 */
public interface Verifier {
    /**
     * Returns normally if [code] is valid for [secret]. Throws [ErrCodeRequired] if [code] is empty,
     * and [ErrInvalidCode] if the code does not validate.
     */
    public suspend fun verify(
        secret: String,
        code: String,
    )
}

/**
 * Builds a TOTP [Verifier]. Port of platform-go's `totp.NewVerifier`.
 *
 * @param clock the time source validation reads; defaults to the system UTC clock. Tests inject a
 *   fixed clock to validate a generated code against a known instant without a real sleep.
 * @param observer the observability sink; defaults to a no-op.
 */
public fun newTotpVerifier(
    observer: Observer = noopObserver(NAME),
    clock: Clock = Clock.systemUTC(),
): Verifier = TotpVerifier(observer, clock)

private class TotpVerifier(
    private val o11y: Observer,
    private val clock: Clock,
) : Verifier {
    override suspend fun verify(
        secret: String,
        code: String,
    ) {
        // Compute the outcome inside the span so the operation always ends cleanly, then surface the
        // failure by throwing outside it — matching platform-go, which returns the sentinel without
        // ever calling op.Error for an empty or non-matching code.
        val outcome =
            o11y.span(NAME) {
                when {
                    code.isEmpty() -> Outcome.CODE_REQUIRED
                    !validateTotpCode(code, secret, clock.instant()) -> Outcome.INVALID
                    else -> Outcome.OK
                }
            }

        when (outcome) {
            Outcome.CODE_REQUIRED -> throw ErrCodeRequired
            Outcome.INVALID -> throw ErrInvalidCode
            Outcome.OK -> Unit
        }
    }

    private enum class Outcome { OK, CODE_REQUIRED, INVALID }
}
