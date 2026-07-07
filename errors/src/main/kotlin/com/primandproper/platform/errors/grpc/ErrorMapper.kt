package com.primandproper.platform.errors.grpc

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
 * Maps domain errors to a [GrpcCode], mirroring Go's `grpc.GRPCErrorMapper`. A `null` return is the
 * `ok=false` "no match" case.
 */
public fun interface GrpcErrorMapper {
    /** Returns the code for [error], or `null` when this mapper does not handle it. */
    public fun map(error: Throwable?): GrpcCode?
}

/**
 * Maps platform-level errors to gRPC codes. Domain-independent, mirroring Go's `grpc.PlatformMapper`.
 */
public object PlatformGrpcMapper : GrpcErrorMapper {
    override fun map(error: Throwable?): GrpcCode? =
        when {
            error == null -> null
            isError(error, ErrUserAlreadyExists) -> GrpcCode.ALREADY_EXISTS
            isError(error, ErrNoRows) -> GrpcCode.NOT_FOUND
            isError(error, ErrCircuitBroken) -> GrpcCode.UNAVAILABLE
            isError(error, ErrNilInputParameter) ||
                isError(error, ErrEmptyInputParameter) ||
                isError(error, ErrNilInputProvided) ||
                isError(error, ErrInvalidIDProvided) ||
                isError(error, ErrEmptyInputProvided) -> GrpcCode.INVALID_ARGUMENT
            else -> null
        }
}

private val domainMappers = CopyOnWriteArrayList<GrpcErrorMapper>()

/**
 * Registers a domain-specific error mapper, mirroring Go's `RegisterGRPCErrorMapper`. Consulted by
 * [mapToGrpc] after [PlatformGrpcMapper].
 */
public fun registerGrpcErrorMapper(mapper: GrpcErrorMapper) {
    domainMappers.add(mapper)
}

/**
 * Returns the gRPC code for [error], mirroring Go's `MapToGRPC`. Tries [PlatformGrpcMapper] first,
 * then each registered domain mapper. A `null` error is [GrpcCode.OK]; an unmatched error yields
 * [defaultCode].
 */
public fun mapToGrpc(
    error: Throwable?,
    defaultCode: GrpcCode,
): GrpcCode {
    if (error == null) return GrpcCode.OK
    PlatformGrpcMapper.map(error)?.let { return it }
    for (mapper in domainMappers) {
        mapper.map(error)?.let { return it }
    }
    return defaultCode
}

/**
 * A carrier of a gRPC [code] plus a human description, the pure-Kotlin analog of a
 * `google.golang.org/grpc/status` error.
 */
public class GrpcStatusException(
    public val code: GrpcCode,
    description: String,
    cause: Throwable? = null,
) : RuntimeException(description, cause)

/**
 * Derives the gRPC code via [mapToGrpc] and returns a [GrpcStatusException], mirroring the shape of
 * Go's `PrepareAndLogGRPCStatus` — minus the logging/tracing, which depend on the observability
 * runtime and are out of scope for this pure module. Returns `null` for a `null` error (maps to
 * [GrpcCode.OK], i.e. "no error to report").
 *
 * TODO: once an observability facade is wired in, add the log/trace side effects Go performs.
 */
public fun prepareGrpcStatus(
    error: Throwable?,
    defaultCode: GrpcCode,
    descriptionFormat: String,
    vararg descriptionArgs: Any?,
): GrpcStatusException? {
    if (error == null) return null
    val code = mapToGrpc(error, defaultCode)
    return GrpcStatusException(code, descriptionFormat.format(*descriptionArgs), error)
}
