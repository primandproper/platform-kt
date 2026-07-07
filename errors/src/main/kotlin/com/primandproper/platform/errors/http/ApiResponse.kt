package com.primandproper.platform.errors.http

/**
 * Details about a response, mirroring platform-go's `http.ResponseDetails`.
 */
public data class ResponseDetails(
    public val currentAccountID: String = "",
    public val traceID: String = "",
)

/**
 * The error payload we might send to a user, mirroring platform-go's `http.APIError`. Like the Go
 * type it is *also* a throwable (Go embeds the `error` interface): [message] is the Throwable
 * message and [errorText] renders the `"<code>: <message>"` form of Go's `Error()`.
 */
public class ApiError(
    override val message: String,
    public val code: ErrorCode,
) : RuntimeException() {
    /** Mirrors Go's `APIError.Error()`: `"<code>: <message>"`. */
    public fun errorText(): String = "${code.value}: $message"

    override fun toString(): String = errorText()
}

/**
 * Mirrors Go's `APIError.AsError()`: returns the error, or `null` for a `null` receiver. Modeled as
 * a nullable-receiver extension so `apiError.asError()` reproduces the Go nil-receiver behavior.
 */
public fun ApiError?.asError(): Throwable? = this

/**
 * A response we might send to the user, mirroring platform-go's generic `http.APIResponse[T]`.
 *
 * The Go type also carries a `*filtering.Pagination`; that lives in the not-yet-ported
 * `database/filtering` module, so it is omitted here (TODO: add once `:database` lands).
 */
public class ApiResponse<T>(
    public val data: T? = null,
    public val error: ApiError? = null,
    public val details: ResponseDetails = ResponseDetails(),
)

/** Builds an error response, mirroring Go's `NewAPIErrorResponse`. */
public fun newApiErrorResponse(
    issue: String,
    code: ErrorCode,
    details: ResponseDetails,
): ApiResponse<Any> = ApiResponse(error = ApiError(issue, code), details = details)
