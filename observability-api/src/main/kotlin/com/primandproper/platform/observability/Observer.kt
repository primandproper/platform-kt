package com.primandproper.platform.observability

/**
 * Bundles a named logger and tracer for a single component, so a class holds one `o11y` field
 * instead of a logger/tracer pair. Each traced operation begins via [begin] — or, idiomatically, the
 * [span] scope — which returns an [Operation] that records selected values to the active span and a
 * span-linked logger at once. Mirrors platform-go's `Observer`.
 *
 * It is an interface so unit tests can substitute a recording implementation (see the
 * `observability-testing` module) and assert which fields a unit observed.
 */
public interface Observer {
    /** The component's span-less named logger, for use outside a traced operation (constructors, background work). */
    public val logger: Logger

    /** The component's underlying tracer. */
    public val tracer: Tracer

    /** Starts a span named [name] and returns an [Operation] carrying a span-linked logger. */
    public fun begin(name: String): Operation
}

internal class DefaultObserver(
    name: String,
    rootLogger: Logger,
    override val tracer: Tracer,
) : Observer {
    override val logger: Logger = namedLogger(rootLogger, name)

    override fun begin(name: String): Operation {
        val span = tracer.startSpan(name)
        return DefaultOperation(span, logger.withSpan(span))
    }
}

/**
 * Builds the production [Observer] from the standard dependencies. The name is applied to both the
 * logger and the tracer, mirroring `observability.NewObserver`.
 */
public fun Observer(name: String, logger: Logger?, tracerProvider: TracerProvider?): Observer =
    DefaultObserver(name, ensureLogger(logger), ensureTracerProvider(tracerProvider).tracer(name))

/** An [Observer] backed by noop logger and tracer, for code that just needs a working Observer in tests. */
public fun noopObserver(name: String): Observer = DefaultObserver(name, NoopLogger, NoopTracer)

/**
 * Hands out per-component [Observer]s from a shared root logger and tracer provider — the typical DI
 * entry point. `factory.named("repo")` is the analog of `NewObserver("repo", logger, tracerProvider)`.
 */
public interface ObserverFactory {
    public val logger: Logger
    public fun named(name: String): Observer
}

public class DefaultObserverFactory(
    private val rootLogger: Logger,
    private val tracerProvider: TracerProvider,
) : ObserverFactory {
    override val logger: Logger get() = rootLogger
    override fun named(name: String): Observer =
        DefaultObserver(name, rootLogger, tracerProvider.tracer(name))
}
