package com.primandproper.platform.errors.grpc

/**
 * The canonical gRPC status codes, mirroring `google.golang.org/grpc/codes.Code`. A pure enum with
 * the same numeric values as the wire protocol, so the mapping stays free of any gRPC runtime
 * dependency. [value] is the on-the-wire integer, should a transport ever need it.
 */
public enum class GrpcCode(public val value: Int) {
    OK(0),
    CANCELLED(1),
    UNKNOWN(2),
    INVALID_ARGUMENT(3),
    DEADLINE_EXCEEDED(4),
    NOT_FOUND(5),
    ALREADY_EXISTS(6),
    PERMISSION_DENIED(7),
    RESOURCE_EXHAUSTED(8),
    FAILED_PRECONDITION(9),
    ABORTED(10),
    OUT_OF_RANGE(11),
    UNIMPLEMENTED(12),
    INTERNAL(13),
    UNAVAILABLE(14),
    DATA_LOSS(15),
    UNAUTHENTICATED(16),
}
