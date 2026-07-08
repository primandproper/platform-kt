package com.primandproper.platform.authentication

/**
 * Hashes passwords. Port of platform-go's `authentication.Hasher`.
 *
 * platform-go threads a `context.Context` through `HashPassword`; this port suspends instead, so the
 * method carries the same cancellation/tracing semantics through the coroutine context.
 */
public interface Hasher {
    /** Hashes [password], returning an encoded digest suitable for storage. */
    public suspend fun hashPassword(password: String): String
}

/**
 * Hashes passwords and verifies them against a stored hash. Port of platform-go's
 * `authentication.Authenticator`.
 *
 * Second-factor verification (TOTP, WebAuthn, backup codes, etc.) is intentionally NOT part of this
 * interface. Callers compose password verification with any second-factor verifier they need — see
 * the [com.primandproper.platform.authentication.totp] package for the TOTP verifier.
 */
public interface Authenticator : Hasher {
    /**
     * Reports whether [password] matches [hash]. A genuine non-match returns `false`; only a
     * malformed hash or a runtime failure throws, mirroring platform-go's `(false, nil)` for a
     * non-match versus a populated `err` for real failures.
     */
    public suspend fun passwordMatches(
        hash: String,
        password: String,
    ): Boolean
}
