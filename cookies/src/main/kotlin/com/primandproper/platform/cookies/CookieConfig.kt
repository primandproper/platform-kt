package com.primandproper.platform.cookies

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The minimum non-zero cookie lifetime, matching platform-go's `minCookieLifetime = 5 * time.Minute`.
 * A [CookieConfig.lifetime] of zero is allowed (a session cookie); any positive lifetime must be at
 * least this, mirroring how Go's `validation.Min` skips the zero value but rejects anything shorter.
 */
internal val MIN_COOKIE_LIFETIME: Duration = 5.minutes

/**
 * Configuration for a [CookieManager]. Port of platform-go's `cookies.Config`.
 *
 * The [base64EncodedHashKey] and [base64EncodedBlockKey] mirror gorilla/securecookie's two-key
 * design (a key for the MAC, a key for the cipher) and are carried here to keep the config surface —
 * and its env-var contract — faithful. Both are folded into the single AES-256-GCM sealing key by
 * [newCookieManager], so both configured secrets remain load-bearing.
 *
 * @param domain the cookie `Domain` attribute; empty leaves it host-only.
 * @param cookieName the default cookie name (Go's `COOKIE_NAME`). Required.
 * @param base64EncodedHashKey the standard-base64-encoded hash (MAC) key. Required.
 * @param base64EncodedBlockKey the standard-base64-encoded block (cipher) key. Required.
 * @param sameSite the `SameSite` policy, case-insensitive; empty defaults to `Lax`. `none` requires
 *   [secureOnly] because browsers silently drop a non-Secure `SameSite=None` cookie.
 * @param lifetime how long a sealed value stays valid and the `Max-Age`/`Expires` a built cookie
 *   carries; zero means a session cookie with no bound lifetime. If positive it must be at least
 *   [MIN_COOKIE_LIFETIME].
 * @param secureOnly whether built cookies carry the `Secure` attribute.
 */
public data class CookieConfig(
    val cookieName: String,
    val base64EncodedHashKey: String,
    val base64EncodedBlockKey: String,
    val domain: String = "",
    val sameSite: String = "",
    val lifetime: Duration = Duration.ZERO,
    val secureOnly: Boolean = false,
) {
    /**
     * Validates the config, throwing [IllegalArgumentException] on any violation. Port of Go's
     * `Config.ValidateWithContext`: name/hash-key/block-key are required, a positive lifetime must
     * meet the minimum, and the `SameSite` value must be recognized (with `none` requiring
     * [secureOnly]).
     */
    public fun validate() {
        require(cookieName.isNotBlank()) { "cookie config: cookieName is required" }
        require(base64EncodedHashKey.isNotBlank()) { "cookie config: base64EncodedHashKey is required" }
        require(base64EncodedBlockKey.isNotBlank()) { "cookie config: base64EncodedBlockKey is required" }

        if (lifetime > Duration.ZERO) {
            require(lifetime >= MIN_COOKIE_LIFETIME) {
                "cookie config: lifetime must be at least $MIN_COOKIE_LIFETIME, was $lifetime"
            }
        }

        when (sameSite.trim().lowercase()) {
            "", SameSite.LAX.value, SameSite.STRICT.value -> Unit
            SameSite.NONE.value ->
                // Browsers silently drop a SameSite=None cookie that is not Secure.
                require(secureOnly) { "cookie config: SameSite=none requires secureOnly" }
            else ->
                throw IllegalArgumentException("cookie config: unsupported SameSite value \"$sameSite\"")
        }
    }
}
