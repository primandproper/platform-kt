package com.primandproper.platform.server

/**
 * gRPC server seam — the named-but-deferred counterpart of platform-go's `server/grpc`.
 *
 * platform-go ships a gRPC server (`grpc.Server`, built with `grpc.NewServer`, OpenTelemetry stats
 * handler, a logging unary interceptor, and reflection). The idiomatic Kotlin equivalent is
 * grpc-kotlin over `io.grpc:grpc-netty` with generated coroutine stubs — a code-generation toolchain
 * and protobuf inputs that this port does not stand up. Rather than drop the concern, the shape is
 * kept here as an explicit `TODO(grpc-kotlin)` so the intended module layout stays visible (the
 * scaffold likewise reserves `server-ktor/.../server/http`, leaving room for a `server/grpc` sibling).
 *
 * [GrpcServerConfig] is the faithful port of `server/grpc.Config`, so config wiring can be written and
 * tested ahead of the server itself — exactly the fields Go validates and binds.
 *
 * TODO(grpc-kotlin): implement a `GrpcServer` in a `:server-grpc` module over grpc-kotlin/grpc-netty,
 *  wiring the OpenTelemetry server handler and a logging interceptor as `server/grpc.NewGRPCServer`
 *  does, and register generated service stubs via a `RegistrationFunc` equivalent.
 */
public data class GrpcServerConfig(
    /** Listen port. Go models this as `uint16`; validated here to the `1..65535` range. */
    val port: Int,
    /** TLS certificate PEM path (Go's `HTTPSCertificateFile`); empty disables TLS. */
    val tlsCertificateFile: String = "",
    /** TLS key PEM path (Go's `TLSCertificateKeyFile`); empty disables TLS. */
    val tlsCertificateKeyFile: String = "",
) {
    /** True when both TLS PEM paths are set, mirroring Go's `TLSCertificateKeyFile != "" && HTTPSCertificateFile != ""`. */
    public val tlsEnabled: Boolean
        get() = tlsCertificateFile.isNotEmpty() && tlsCertificateKeyFile.isNotEmpty()

    /** Validates the config, throwing [IllegalArgumentException] on the first problem. */
    public fun validate() {
        require(port in 1..65_535) { "grpc server: port must be in 1..65535, was $port" }
    }
}
