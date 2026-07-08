package com.primandproper.platform.httpclient

/**
 * Header redaction — a **security property, not a feature** (called out in docs/PORTING_STATUS.md as
 * the one thing that must arrive *with* HTTP span integration). When request/response data is
 * attached to a span, credential-bearing headers are replaced with [PLACEHOLDER] **before** anything
 * is recorded, so tokens and cookies never reach a trace exporter or log. Both the OkHttp and Ktor
 * backends route through here, so the redaction set is defined once. Mirrors what platform-go's
 * httpclient/otel path redacts.
 *
 * Matching is case-insensitive (see [HttpHeaders]); the set is intentionally broad — anything that
 * carries a credential, a session, or an auth challenge.
 */
public object HeaderRedaction {
    /** The value substituted for a sensitive header. */
    public const val PLACEHOLDER: String = "REDACTED"

    /** Sensitive header names, lowercased. */
    public val SENSITIVE_HEADERS: Set<String> =
        setOf(
            "authorization",
            "proxy-authorization",
            "www-authenticate",
            "proxy-authenticate",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "x-auth-token",
            "x-csrf-token",
            "x-xsrf-token",
        )

    /** True if [name] names a header whose value must be redacted before recording. */
    public fun isSensitive(name: String): Boolean = name.lowercase() in SENSITIVE_HEADERS

    /** Returns [values] unchanged, or a same-length list of [PLACEHOLDER]s when [name] is sensitive. */
    public fun redactValues(
        name: String,
        values: List<String>,
    ): List<String> = if (isSensitive(name)) values.map { PLACEHOLDER } else values

    /** A copy of [headers] safe to record: sensitive values replaced with [PLACEHOLDER]. */
    public fun redact(headers: HttpHeaders): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        headers.forEach { name, values -> out[name] = redactValues(name, values) }
        return out
    }
}
