package com.primandproper.platform.email.resend

/**
 * Configures the Resend backend. Port of platform-go's `resend.Config`.
 *
 * The single required field is the API token; Go validates it as `validation.Required`, mirrored here
 * by [validate] (and by the [EmptyApiTokenException] the [ResendEmailer] factory throws on an empty
 * token). The [baseUrl] is not part of Go's `Config` — Resend's SDK hardcodes the host and tests
 * override `client.BaseURL` — but it is surfaced here so tests can point the emailer at a fake host
 * and production can override the endpoint if needed; it defaults to Resend's real API base.
 */
public data class ResendConfig(
    val apiToken: String,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** Throws [EmptyApiTokenException] when [apiToken] is empty, mirroring Go's `Required` rule. */
    public fun validate() {
        if (apiToken.isEmpty()) throw EmptyApiTokenException()
    }

    public companion object {
        /** Resend's production API base. Requests target `<baseUrl>/emails`. */
        public const val DEFAULT_BASE_URL: String = "https://api.resend.com"
    }
}
