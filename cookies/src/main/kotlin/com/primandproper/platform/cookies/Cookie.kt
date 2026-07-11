package com.primandproper.platform.cookies

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The `SameSite` policy of a cookie. Port of the `SameSiteLax`/`SameSiteStrict`/`SameSiteNone`
 * constants and `http.SameSite` modes platform-go maps between.
 *
 * [value] is the lowercase config/wire form (what [CookieConfig.sameSite] accepts); [headerValue] is
 * the capitalized token emitted in a `Set-Cookie` header.
 */
public enum class SameSite(
    public val value: String,
    public val headerValue: String,
) {
    LAX("lax", "Lax"),
    STRICT("strict", "Strict"),
    NONE("none", "None"),
    ;

    public companion object {
        /**
         * Resolves a policy from its config [raw] string (trimmed, case-insensitive), defaulting to
         * [LAX] for the empty — or any unexpected — value. Mirrors Go's `sameSiteMode`, which relies
         * on [CookieConfig.validate] having already rejected genuinely unsupported values.
         */
        public fun fromConfigValue(raw: String): SameSite =
            when (raw.trim().lowercase()) {
                STRICT.value -> STRICT
                NONE.value -> NONE
                else -> LAX
            }
    }
}

/**
 * A ready-to-set cookie: the sealed [value] plus the security attributes a [CookieManager] applies.
 * Port of the fields platform-go populates on `*http.Cookie` in `BuildCookie`.
 *
 * [HttpOnly][httpOnly] defaults to `true` — the non-negotiable default Go hard-codes — and [maxAge]
 * / [expires] are `null` for a session cookie (no bound lifetime).
 */
public data class Cookie(
    val name: String,
    val value: String,
    val path: String = "/",
    val domain: String = "",
    val httpOnly: Boolean = true,
    val secure: Boolean = false,
    val sameSite: SameSite = SameSite.LAX,
    val maxAge: Int? = null,
    val expires: Instant? = null,
) {
    /**
     * Serializes this cookie to a `Set-Cookie` header value, in the attribute order Go's
     * `http.Cookie.String()` emits: `name=value` then `Path`, `Domain`, `Expires`, `Max-Age`,
     * `HttpOnly`, `Secure`, `SameSite`. Empty [path]/[domain] and a null [expires]/[maxAge] are
     * omitted.
     *
     * `Max-Age` mirrors Go's `http.Cookie` sentinel semantics: a zero value is treated as "unset"
     * and the attribute is omitted; a negative value emits `Max-Age=0` (delete now); a positive
     * value emits the count of seconds.
     */
    public fun serialize(): String {
        val sb = StringBuilder()
        sb.append(name).append('=').append(value)
        if (path.isNotEmpty()) sb.append("; Path=").append(path)
        if (domain.isNotEmpty()) sb.append("; Domain=").append(domain)
        expires?.let { sb.append("; Expires=").append(HTTP_DATE.format(it.atOffset(ZoneOffset.UTC))) }
        maxAge?.let {
            when {
                it > 0 -> sb.append("; Max-Age=").append(it)
                it < 0 -> sb.append("; Max-Age=0")
                // it == 0: Go omits the attribute entirely (0 means "unset"), so emit nothing.
                else -> Unit
            }
        }
        if (httpOnly) sb.append("; HttpOnly")
        if (secure) sb.append("; Secure")
        sb.append("; SameSite=").append(sameSite.headerValue)
        return sb.toString()
    }

    private companion object {
        // IMF-fixdate, matching Go's http.TimeFormat ("Mon, 02 Jan 2006 15:04:05 GMT"): a fixed-width,
        // zero-padded 2-digit day. RFC_1123_DATE_TIME uses a variable-width day, which renders the 1st
        // through 9th as a single digit and trips strict RFC 7231 parsers.
        private val HTTP_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    }
}
