package com.primandproper.platform.observability

/** Selects the logging backend. */
public enum class LoggingProvider { LOGCAT, NOOP }

/** Selects the tracing backend. */
public enum class TracingProvider { OTEL, NOOP }

public class LoggingConfig {
    public var provider: LoggingProvider = LoggingProvider.LOGCAT
    public var level: Level = Level.INFO
}

public class TracingConfig {
    public var provider: TracingProvider = TracingProvider.OTEL

    /** OTLP collector endpoint, e.g. `https://collector:4317` (gRPC) or `.../v1/traces` (HTTP). */
    public var endpoint: String = ""

    /** Head sampling probability in `[0, 1]`. `1.0` records everything; `0.0` records nothing. */
    public var sampleRatio: Double = 1.0

    /** Use OTLP/HTTP instead of OTLP/gRPC. */
    public var useHttp: Boolean = false
}

/**
 * Aggregate configuration for the observability pillars — the schema the `Observability { }` builder
 * reads. Replaces platform-go's env-tagged `Config`; on Android the values come from build flavors,
 * `BuildConfig`, or DI rather than the environment. [validate] is the `ValidateWithContext` analog,
 * reporting misconfiguration by throwing rather than returning an error.
 */
public class ObservabilityConfig {
    public var serviceName: String = ""
    public val logging: LoggingConfig = LoggingConfig()
    public val tracing: TracingConfig = TracingConfig()

    public fun logging(block: LoggingConfig.() -> Unit) {
        logging.block()
    }

    public fun tracing(block: TracingConfig.() -> Unit) {
        tracing.block()
    }

    public fun validate() {
        require(serviceName.isNotBlank()) { "observability: serviceName is required" }
        if (tracing.provider == TracingProvider.OTEL) {
            require(tracing.endpoint.isNotBlank()) {
                "observability: tracing.endpoint is required for the OTEL provider"
            }
            require(tracing.sampleRatio in 0.0..1.0) {
                "observability: tracing.sampleRatio must be in [0, 1], was ${tracing.sampleRatio}"
            }
        }
    }
}
