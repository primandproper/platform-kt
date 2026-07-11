package com.primandproper.platform.observability

import com.primandproper.platform.observability.logcat.LogcatLogger
import com.primandproper.platform.observability.otel.OtelTracerProvider

/**
 * Assembles the observability stack from a config block — the Android-idiomatic analog of
 * platform-go's `Config.ProvidePillars`. Validates first (throwing on misconfiguration), then wires
 * the selected backends.
 *
 * ```
 * val o11y = Observability {
 *     serviceName = "my-app"
 *     logging { provider = LoggingProvider.LOGCAT; level = Level.DEBUG }
 *     tracing { provider = TracingProvider.OTEL; endpoint = "https://collector:4317"; sampleRatio = 0.1 }
 * }
 * val observers = o11y.observers
 * ```
 */
public fun Observability(configure: ObservabilityConfig.() -> Unit): Observability {
    val cfg = ObservabilityConfig().apply(configure)
    cfg.validate()

    val logger: Logger =
        when (cfg.logging.provider) {
            LoggingProvider.LOGCAT -> LogcatLogger(tag = cfg.serviceName, minLevel = cfg.logging.level)
            LoggingProvider.NOOP -> NoopLogger
        }

    val tracerProvider: TracerProvider =
        when (cfg.tracing.provider) {
            TracingProvider.OTEL ->
                OtelTracerProvider.create(
                    serviceName = cfg.serviceName,
                    // Non-null here: cfg.validate() above requires a non-blank endpoint for the OTEL provider.
                    endpoint = checkNotNull(cfg.tracing.endpoint),
                    sampleRatio = cfg.tracing.sampleRatio,
                    useHttp = cfg.tracing.useHttp,
                )
            TracingProvider.NOOP -> NoopTracerProvider
        }

    return DefaultObservability(logger, tracerProvider)
}
