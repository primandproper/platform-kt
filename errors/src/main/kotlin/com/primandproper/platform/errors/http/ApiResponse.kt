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
 * A response we might send to the user, mirroring platform-go's generic `http.APIResponse[T]`.
 *
 * Go models this as one struct with nullable `data` and `error` fields (an untagged union where both
 * or neither could be set). Here it is a sealed hierarchy — [Success] carries the payload, [Failure]
 * carries the [ApiError] — so "has data XOR has error" is enforced by the type system and callers
 * `when`-exhaust over the two cases instead of null-checking two fields.
 *
 * The Go type also carries a `*filtering.Pagination`; that lives in the not-yet-ported
 * `database/filtering` module, so it is omitted here (TODO: add once `:database` lands).
 */
public sealed class ApiResponse<out T> {
    /** Details common to both outcomes (trace id, current account id). */
    public abstract val details: ResponseDetails

    /** A successful response carrying [data]. */
    public data class Success<out T>(
        public val data: T,
        override val details: ResponseDetails = ResponseDetails(),
    ) : ApiResponse<T>()

    /** A failed response carrying the user-facing [error]. */
    public data class Failure(
        public val error: ApiError,
        override val details: ResponseDetails = ResponseDetails(),
    ) : ApiResponse<Nothing>()
}

/** Builds an error response, mirroring Go's `NewAPIErrorResponse`. */
public fun newApiErrorResponse(
    issue: String,
    code: ErrorCode,
    details: ResponseDetails,
): ApiResponse.Failure = ApiResponse.Failure(ApiError(issue, code), details)
