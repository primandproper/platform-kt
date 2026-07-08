package com.primandproper.platform.cookies

import com.primandproper.platform.cryptography.encryption.EncryptorDecryptor
import com.primandproper.platform.cryptography.encryption.aes.newAesEncryptorDecryptor
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val OBSERVER_NAME = "cookie_manager"
private const val SEALER_NAME = "cookie_sealer"

private const val SPAN_ENCODE = "encode"
private const val SPAN_DECODE = "decode"
private const val SPAN_BUILD = "build_cookie"

/** The crypto expiry cap applied to a sealed value when no positive lifetime is configured, matching
 * gorilla/securecookie's default 30-day MaxAge. */
private val SEAL_DEFAULT_MAX_AGE = 30.days

/**
 * Seals cookie values and builds ready-to-set cookies. Port of platform-go's `cookies.Manager`.
 *
 * Values are `String`s: serialization stays at the boundary, exactly as in the `:cache` port — a
 * caller with a richer type serializes it (e.g. to JSON) before [encode] and parses it after
 * [decode]. This mirrors the effect of Go's gob-then-securecookie pipeline without pulling a
 * serialization framework into the seam, and it removes Go's "unencodable value" failure path (a
 * `String` always seals).
 *
 * Each method threads a span through the coroutine context instead of Go's explicit `ctx` and
 * records the cookie [name][Keys.NAME], mirroring how Go wraps every method in an `Observer`
 * operation.
 */
public interface CookieManager {
    /** Seals [value] under [name] into a signed, encrypted, tamper-evident string. */
    public suspend fun encode(
        name: String,
        value: String,
    ): String

    /**
     * Unseals a [value] previously produced by [encode] under the same [name], returning the plaintext.
     * Throws [CookieException] if the value is tampered with, was sealed under a different name, or has
     * outlived the configured lifetime.
     */
    public suspend fun decode(
        name: String,
        value: String,
    ): String

    /**
     * Seals [value] and returns a [Cookie] carrying the configured security attributes: `Secure` from
     * `secureOnly`, the configured `Domain` and `SameSite`, `Max-Age`/`Expires` from the lifetime, plus
     * a non-negotiable `HttpOnly`. Port of Go's `BuildCookie`.
     */
    public suspend fun buildCookie(
        name: String,
        value: String,
    ): Cookie
}

/**
 * Raised when a cookie cannot be sealed or unsealed. Its message never echoes the cookie value or
 * any key material, mirroring how Go's wrapped errors keep secrets out of logs.
 */
public class CookieException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Builds a [CookieManager] from [config]. Port of platform-go's `NewCookieManager`.
 *
 * The config is validated first (throwing [IllegalArgumentException] on a bad `SameSite`, missing
 * key, or too-short lifetime), then the two base64 keys are decoded and folded — via SHA-256 — into
 * the single 32-byte AES-256-GCM key that seals every value. GCM's authenticated encryption
 * subsumes gorilla/securecookie's separate encrypt-and-MAC: it gives confidentiality and tamper
 * detection in one pass, and a fresh random nonce per seal means the same value yields a different
 * ciphertext each time.
 *
 * @param observer the observability sink for the manager's own operations; defaults to a no-op.
 *   Tests inject a recording observer. The internal sealing engine is deliberately given a *noop*
 *   observer so instrumentation lives only at the manager boundary, exactly as in Go.
 */
public fun newCookieManager(
    config: CookieConfig,
    observer: Observer = noopObserver(OBSERVER_NAME),
): CookieManager = buildCookieManager(config, observer) { Instant.now() }

/**
 * The clock-injectable builder behind [newCookieManager]. `internal` so tests in this package can
 * drive lifetime/expiry logic with a controllable [now] without exposing a clock seam on the public
 * factory.
 */
internal fun buildCookieManager(
    config: CookieConfig,
    observer: Observer,
    now: () -> Instant,
): CookieManager {
    config.validate()

    val hashKey = decodeKey(config.base64EncodedHashKey, "hash key")
    val blockKey = decodeKey(config.base64EncodedBlockKey, "block key")

    // Fold both configured secrets into the single AES-256 key so each remains load-bearing and any
    // key length is accepted (SHA-256 always yields the required 32 bytes), the way gorilla accepts
    // flexible key sizes. The sealer is given a noop observer: only the manager instruments.
    val sealKey = deriveSealKey(hashKey, blockKey)
    val sealer = newAesEncryptorDecryptor(sealKey, noopObserver(SEALER_NAME))

    return DefaultCookieManager(
        sealer = sealer,
        o11y = observer,
        domain = config.domain,
        lifetime = config.lifetime,
        sameSite = SameSite.fromConfigValue(config.sameSite),
        secureOnly = config.secureOnly,
        now = now,
    )
}

private fun decodeKey(
    encoded: String,
    label: String,
): ByteArray =
    try {
        Base64.getDecoder().decode(encoded)
    } catch (e: IllegalArgumentException) {
        // Report the failure without echoing the (secret) key material.
        throw IllegalArgumentException("decoding $label: not valid base64", e)
    }

private fun deriveSealKey(
    hashKey: ByteArray,
    blockKey: ByteArray,
): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(hashKey)
    digest.update(blockKey)
    return digest.digest()
}

internal class DefaultCookieManager(
    private val sealer: EncryptorDecryptor,
    private val o11y: Observer,
    private val domain: String,
    private val lifetime: Duration,
    private val sameSite: SameSite,
    private val secureOnly: Boolean,
    private val now: () -> Instant,
) : CookieManager {
    override suspend fun encode(
        name: String,
        value: String,
    ): String =
        o11y.span(SPAN_ENCODE) {
            set(Keys.NAME, name)
            seal(name, value)
        }

    override suspend fun decode(
        name: String,
        value: String,
    ): String =
        o11y.span(SPAN_DECODE) {
            set(Keys.NAME, name)
            unseal(name, value)
        }

    override suspend fun buildCookie(
        name: String,
        value: String,
    ): Cookie =
        o11y.span(SPAN_BUILD) {
            set(Keys.NAME, name)

            val encoded = seal(name, value)
            val cookie =
                Cookie(
                    name = name,
                    value = encoded,
                    path = "/",
                    domain = domain,
                    httpOnly = true,
                    secure = secureOnly,
                    sameSite = sameSite,
                )

            if (lifetime > Duration.ZERO) {
                cookie.copy(
                    maxAge = lifetime.inWholeSeconds.toInt(),
                    expires = now().plusMillis(lifetime.inWholeMilliseconds),
                )
            } else {
                cookie
            }
        }

    // The sealed plaintext binds a timestamp (for lifetime enforcement, the analog of
    // securecookie.MaxAge) and the cookie name (so a value cannot be replayed under a different
    // name, the analog of gorilla mixing the name into the MAC), then the value. The name is
    // base64url-encoded so it carries no delimiter; the value — which may contain '|' — is the
    // remainder after the first two separators.
    private suspend fun seal(
        name: String,
        value: String,
    ): String {
        val timestamp = now().toEpochMilli()
        val boundName = Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))
        return sealer.encrypt("$timestamp|$boundName|$value")
    }

    private suspend fun unseal(
        name: String,
        sealed: String,
    ): String {
        // Any decrypt failure (bad base64, wrong key, or a flipped byte failing the GCM tag) means
        // the value is not authentic.
        val payload =
            try {
                sealer.decrypt(sealed)
            } catch (e: Exception) {
                throw CookieException("decoding cookie: value is not authentic", e)
            }

        val parts = payload.split("|", limit = 3)
        if (parts.size != 3) throw CookieException("decoding cookie: malformed payload")

        val timestamp =
            parts[0].toLongOrNull() ?: throw CookieException("decoding cookie: malformed timestamp")

        val boundName =
            try {
                String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw CookieException("decoding cookie: malformed name", e)
            }
        if (boundName != name) throw CookieException("decoding cookie: cookie name mismatch")

        // A sealed value is always cryptographically time-bounded. When lifetime is 0 (an HTTP session
        // cookie — no Max-Age attribute), Go's gorilla/securecookie still caps the value at its 30-day
        // default MaxAge; mirror that so a leaked session cookie can't be replayed forever.
        val cryptoLifetime = if (lifetime > Duration.ZERO) lifetime else SEAL_DEFAULT_MAX_AGE
        val ageMillis = now().toEpochMilli() - timestamp
        if (ageMillis < 0 || ageMillis > cryptoLifetime.inWholeMilliseconds) {
            throw CookieException("decoding cookie: value has expired")
        }

        return parts[2]
    }
}
