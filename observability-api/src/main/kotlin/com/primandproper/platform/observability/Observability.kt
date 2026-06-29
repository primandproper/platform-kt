package com.primandproper.platform.observability

/**
 * The assembled observability stack: the root logger, the tracer provider, and the [ObserverFactory]
 * components draw their per-component [Observer]s from. The analog of platform-go's `Pillars` plus
 * its `ProvidePillars` result. Construct it with the `Observability { }` builder in the
 * `:observability` umbrella module, which wires the concrete backends from [ObservabilityConfig].
 */
public interface Observability {
    public val logger: Logger
    public val tracerProvider: TracerProvider
    public val observers: ObserverFactory

    /** Flushes and tears down exporters. Call from `Application.onTerminate` / a shutdown hook. */
    public fun shutdown()
}

/** A ready-made [Observability] over an existing logger and tracer provider. */
public class DefaultObservability(
    override val logger: Logger,
    override val tracerProvider: TracerProvider,
) : Observability {
    override val observers: ObserverFactory = DefaultObserverFactory(logger, tracerProvider)

    override fun shutdown() {
        tracerProvider.forceFlush()
        tracerProvider.shutdown()
    }
}
