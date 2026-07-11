package com.primandproper.platform.errors.http

import com.primandproper.platform.errors.CircuitBrokenException
import com.primandproper.platform.errors.EmptyInputParameterException
import com.primandproper.platform.errors.EmptyInputProvidedException
import com.primandproper.platform.errors.InvalidIDProvidedException
import com.primandproper.platform.errors.NoRowsException
import com.primandproper.platform.errors.UserAlreadyExistsException
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
            isError<NoRowsException>(error) ->
                HttpMapping(ErrorCode.ErrDataNotFound, "data not found")
            isError<UserAlreadyExistsException>(error) ->
                HttpMapping(ErrorCode.ErrValidatingRequestInput, "user already exists")
            isError<CircuitBrokenException>(error) ->
                HttpMapping(ErrorCode.ErrCircuitBroken, "service temporarily unavailable")
            isError<EmptyInputParameterException>(error) ||
                isError<InvalidIDProvidedException>(error) ||
                isError<EmptyInputProvidedException>(error) ->
                HttpMapping(ErrorCode.ErrValidatingRequestInput, "invalid input")
            else -> null
        }
}

/**
 * A registry of domain-specific [HttpErrorMapper]s, consulted after the built-in [PlatformHttpMapper].
 * Instantiable (mirroring Go's `RegisterHTTPErrorMapper` + `ToAPIError`, but scoped to an instance
 * rather than a process-global list) so an application owns its own registry and tests get a fresh,
 * isolated one instead of contending on shared mutable state. The internal list is thread-safe, so a
 * single shared registry can still be populated at startup and read concurrently.
 */
public class HttpErrorMapperRegistry {
    private val domainMappers = CopyOnWriteArrayList<HttpErrorMapper>()

    /**
     * Registers a domain-specific error mapper. Domains call this at startup to contribute their
     * mappings; consulted by [toApiError] after [PlatformHttpMapper].
     */
    public fun register(mapper: HttpErrorMapper) {
        domainMappers.add(mapper)
    }

    /**
     * Maps a known error to an [ErrorCode] and safe, user-facing message, mirroring Go's `ToAPIError`.
     * Tries [PlatformHttpMapper] first, then each registered domain mapper. Unknown errors fall back
     * to the neutral [ErrorCode.ErrNothingSpecific] / `"an error occurred"`, never a domain-specific
     * code.
     */
    public fun toApiError(error: Throwable?): HttpMapping {
        if (error == null) return HttpMapping(ErrorCode.ErrNothingSpecific, "")
        PlatformHttpMapper.map(error)?.let { return it }
        for (mapper in domainMappers) {
            mapper.map(error)?.let { return it }
        }
        return HttpMapping(ErrorCode.ErrNothingSpecific, "an error occurred")
    }
}
