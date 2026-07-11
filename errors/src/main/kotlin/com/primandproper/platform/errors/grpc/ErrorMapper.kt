package com.primandproper.platform.errors.grpc

import com.primandproper.platform.errors.CircuitBrokenException
import com.primandproper.platform.errors.EmptyInputParameterException
import com.primandproper.platform.errors.EmptyInputProvidedException
import com.primandproper.platform.errors.InvalidIDProvidedException
import com.primandproper.platform.errors.NoRowsException
import com.primandproper.platform.errors.UserAlreadyExistsException
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
            isError<UserAlreadyExistsException>(error) -> GrpcCode.ALREADY_EXISTS
            isError<NoRowsException>(error) -> GrpcCode.NOT_FOUND
            isError<CircuitBrokenException>(error) -> GrpcCode.UNAVAILABLE
            isError<EmptyInputParameterException>(error) ||
                isError<InvalidIDProvidedException>(error) ||
                isError<EmptyInputProvidedException>(error) -> GrpcCode.INVALID_ARGUMENT
            else -> null
        }
}

/**
 * A registry of domain-specific [GrpcErrorMapper]s, consulted after the built-in [PlatformGrpcMapper].
 * Instantiable (mirroring Go's `RegisterGRPCErrorMapper` + `MapToGRPC`, but scoped to an instance
 * rather than a process-global list) so an application owns its own registry and tests get a fresh,
 * isolated one. The internal list is thread-safe, so a single shared registry can still be populated
 * at startup and read concurrently.
 */
public class GrpcErrorMapperRegistry {
    private val domainMappers = CopyOnWriteArrayList<GrpcErrorMapper>()

    /**
     * Registers a domain-specific error mapper. Consulted by [mapToGrpc] after [PlatformGrpcMapper].
     */
    public fun register(mapper: GrpcErrorMapper) {
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
     * Derives the gRPC code via [mapToGrpc] and returns a [GrpcStatusException], mirroring the shape
     * of Go's `PrepareAndLogGRPCStatus` — minus the logging/tracing, which depend on the observability
     * runtime and are out of scope for this pure module. Returns `null` for a `null` error (maps to
     * [GrpcCode.OK], i.e. "no error to report"). Build [description] with a Kotlin string template.
     *
     * TODO: once an observability facade is wired in, add the log/trace side effects Go performs.
     */
    public fun prepareGrpcStatus(
        error: Throwable?,
        defaultCode: GrpcCode,
        description: String,
    ): GrpcStatusException? {
        if (error == null) return null
        val code = mapToGrpc(error, defaultCode)
        return GrpcStatusException(code, description, error)
    }
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
