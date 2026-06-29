package com.primandproper.platform.observability

// `Span` is the package-level typealias declared in Span.kt (to OTel's trace.Span).

/**
 * Starts spans for a single named component. The new span is parented to the OpenTelemetry context
 * currently in scope (which, in suspend code, flows through the coroutine context — see [span]).
 */
public interface Tracer {
    /** Starts and returns a span named [name]. The span is not made current; [span]/[spanBlocking] handle that. */
    public fun startSpan(name: String): Span
}

/**
 * Builds named [Tracer]s and owns the export lifecycle. Mirrors platform-go's
 * `tracing.TracerProvider` (which embeds OTel's provider and adds `ForceFlush`).
 */
public interface TracerProvider {
    public fun tracer(name: String): Tracer

    /** Flushes any buffered spans to the exporter. Call on app background / before shutdown. */
    public fun forceFlush()

    public fun shutdown()
}

/** Builds a named tracer, mirroring `tracing.NewNamedTracer`. */
public fun namedTracer(provider: TracerProvider?, name: String): Tracer =
    ensureTracerProvider(provider).tracer(name)

/** Returns [provider] if non-null, otherwise a [NoopTracerProvider]. Mirrors `EnsureTracerProvider`. */
public fun ensureTracerProvider(provider: TracerProvider?): TracerProvider = provider ?: NoopTracerProvider

/** A tracer whose spans never record. The safe default and the test stand-in. */
public object NoopTracer : Tracer {
    override fun startSpan(name: String): Span = io.opentelemetry.api.trace.Span.getInvalid()
}

public object NoopTracerProvider : TracerProvider {
    override fun tracer(name: String): Tracer = NoopTracer
    override fun forceFlush() {}
    override fun shutdown() {}
}
