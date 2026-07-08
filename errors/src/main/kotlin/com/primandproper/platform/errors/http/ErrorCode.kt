package com.primandproper.platform.errors.http

/**
 * A string code identifying a specific error condition in an API response, mirroring platform-go's
 * `http.ErrorCode`. Modeled as a value class rather than a closed enum because domains may register
 * their own codes (e.g. `"E_CUSTOM"`) through [RegisterHttpErrorMapper]; the platform-owned codes
 * are the `E1xx` constants on the companion.
 *
 * [toHttpStatus] is the pure mapping the task calls for: platform-go leaves the HTTP status to the
 * server framework, but since callers here have no framework to lean on we assign a sensible status
 * to each platform code (unknown/custom codes fall back to 500).
 */
@JvmInline
public value class ErrorCode(public val value: String) {
    /** Maps this code to an HTTP status. Unknown codes default to 500 (Internal Server Error). */
    public fun toHttpStatus(): Int =
        when (this) {
            ErrDecodingRequestInput, ErrValidatingRequestInput -> 400
            ErrUserIsBanned, ErrUserIsNotAuthorized -> 403
            ErrDataNotFound -> 404
            ErrMisbehavingDependency -> 502
            ErrCircuitBroken -> 503
            else -> 500
        }

    public companion object {
        /** Catch-all code for when we just need one. */
        public val ErrNothingSpecific: ErrorCode = ErrorCode("E100")

        /** Failed to fetch session context data. */
        public val ErrFetchingSessionContextData: ErrorCode = ErrorCode("E101")

        /** Failed to decode request input. */
        public val ErrDecodingRequestInput: ErrorCode = ErrorCode("E102")

        /** The user provided invalid input. */
        public val ErrValidatingRequestInput: ErrorCode = ErrorCode("E103")

        /** Failed to find data in the database. */
        public val ErrDataNotFound: ErrorCode = ErrorCode("E104")

        /** Failed to interact with a database. */
        public val ErrTalkingToDatabase: ErrorCode = ErrorCode("E105")

        /** Failed to interact with a third party. */
        public val ErrMisbehavingDependency: ErrorCode = ErrorCode("E106")

        /** Failed to interact with the search provider. */
        public val ErrTalkingToSearchProvider: ErrorCode = ErrorCode("E107")

        /** Failed to generate a secret. */
        public val ErrSecretGeneration: ErrorCode = ErrorCode("E108")

        /** The user is banned. */
        public val ErrUserIsBanned: ErrorCode = ErrorCode("E109")

        /** The user is not authorized. */
        public val ErrUserIsNotAuthorized: ErrorCode = ErrorCode("E110")

        /** Encryption failed in the service. */
        public val ErrEncryptionIssue: ErrorCode = ErrorCode("E111")

        /** A service is circuit broken. */
        public val ErrCircuitBroken: ErrorCode = ErrorCode("E112")
    }
}
