package com.primandproper.platform.observability

/**
 * Standard attribute keys for structured logging and span attributes, so a value is named the same
 * way wherever it is recorded. Port of `observability/keys/keys.go`, trimmed to the keys that make
 * sense on an Android client (server-only keys — query filters, gRPC — were dropped).
 *
 * Where a key overlaps an OpenTelemetry semantic convention, the convention name is used so traces
 * stay portable across tooling.
 */
public object Keys {
    public const val NAME: String = "name"
    public const val SPAN_ID: String = "span.id"
    public const val TRACE_ID: String = "trace.id"

    public const val SERVICE_NAME: String = "service.name"

    public const val REQUEST_ID: String = "request.id"
    public const val REQUEST_METHOD: String = "http.request.method"
    public const val REQUEST_URI: String = "url.full"
    public const val RESPONSE_STATUS: String = "http.response.status_code"

    public const val USER_ID: String = "user.id"
    public const val USERNAME: String = "user.name"
    public const val ACTIVE_ACCOUNT_ID: String = "active_account.id"
    public const val USER_IS_SERVICE_ADMIN: String = "user.is_admin"

    public const val REASON: String = "reason"
    public const val VALIDATION_ERROR: String = "validation_error"
    public const val LENGTH: String = "length"
    public const val FILENAME: String = "filename"
    public const val CONNECTION_URL: String = "connection.url"
    public const val SEARCH_QUERY: String = "search.query"
}
