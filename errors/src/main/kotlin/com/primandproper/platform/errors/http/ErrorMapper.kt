package com.primandproper.platform.errors.http

import com.primandproper.platform.errors.ErrCircuitBroken
import com.primandproper.platform.errors.ErrEmptyInputParameter
import com.primandproper.platform.errors.ErrEmptyInputProvided
import com.primandproper.platform.errors.ErrInvalidIDProvided
import com.primandproper.platform.errors.ErrNilInputParameter
import com.primandproper.platform.errors.ErrNilInputProvided
import com.primandproper.platform.errors.ErrNoRows
import com.primandproper.platform.errors.ErrUserAlreadyExists
import com.primandproper.platform.errors.isError
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A ([ErrorCode], message) pair produced by an [HttpErrorMapper]. Replaces Go's
 * `(code, msg, ok bool)` triple: a `null` return is the `ok=false` "no match" case.
 */
public data class HttpMapping(
    public val code: ErrorCode,
    public val message: String,
)

/** Maps domain errors to a ([ErrorCode], message), mirroring Go's `http.HTTPErrorMapper`. */
public fun interface HttpErrorMapper {
    /** Returns the mapping for [error], or `null` when this mapper does not handle it. */
    public fun map(error: Throwable?): HttpMapping?
}

/**
 * Maps platform-level errors to HTTP error codes and messages. Domain-independent, mirroring Go's
 * `http.PlatformMapper`.
 */
public object PlatformHttpMapper : HttpErrorMapper {
    override fun map(error: Throwable?): HttpMapping? =
        when {
            error == null -> null
            isError(error, ErrNoRows) ->
                HttpMapping(ErrorCode.ErrDataNotFound, "data not found")
            isError(error, ErrUserAlreadyExists) ->
                HttpMapping(ErrorCode.ErrValidatingRequestInput, "user already exists")
            isError(error, ErrCircuitBroken) ->
                HttpMapping(ErrorCode.ErrCircuitBroken, "service temporarily unavailable")
            isError(error, ErrNilInputParameter) ||
                isError(error, ErrEmptyInputParameter) ||
                isError(error, ErrNilInputProvided) ||
                isError(error, ErrInvalidIDProvided) ||
                isError(error, ErrEmptyInputProvided) ->
                HttpMapping(ErrorCode.ErrValidatingRequestInput, "invalid input")
            else -> null
        }
}

private val domainMappers = CopyOnWriteArrayList<HttpErrorMapper>()

/**
 * Registers a domain-specific error mapper, mirroring Go's `RegisterHTTPErrorMapper`. Domains call
 * this at startup to contribute their mappings; consulted by [toApiError] after [PlatformHttpMapper].
 */
public fun registerHttpErrorMapper(mapper: HttpErrorMapper) {
    domainMappers.add(mapper)
}

/**
 * Maps a known error to an [ErrorCode] and safe, user-facing message, mirroring Go's `ToAPIError`.
 * Tries [PlatformHttpMapper] first, then each registered domain mapper. Unknown errors fall back to
 * the neutral [ErrorCode.ErrNothingSpecific] / `"an error occurred"`, never a domain-specific code.
 */
public fun toApiError(error: Throwable?): HttpMapping {
    if (error == null) return HttpMapping(ErrorCode.ErrNothingSpecific, "")
    PlatformHttpMapper.map(error)?.let { return it }
    for (mapper in domainMappers) {
        mapper.map(error)?.let { return it }
    }
    return HttpMapping(ErrorCode.ErrNothingSpecific, "an error occurred")
}
