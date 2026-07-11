package com.primandproper.platform.observability

// `Span` is the package-level typealias declared in Span.kt (to OTel's trace.Span).

/**
 * Logging severity, mirroring platform-go's pre-created level singletons.
 */
public enum class Level { DEBUG, INFO, WARN, ERROR }

/**
 * Structured logger facade. Backends (Logcat, OTel logs) implement it; tests substitute a noop or a
 * recording double. Every `with*` returns a new [Logger] carrying the added context, so callers
 * compose without mutating shared state — the fluent style of platform-go's `logging.Logger`.
 *
 * [error] keeps the Go signature deliberately: a human description of what was happening when the
 * error occurred, plus the throwable.
 */
public interface Logger {
    public fun debug(msg: String)

    public fun info(msg: String)

    public fun warn(msg: String)

    public fun error(
        whatWasHappening: String,
        err: Throwable?,
    )

    public fun withName(name: String): Logger

    public fun withValue(
        key: String,
        value: Any?,
    ): Logger

    public fun withValues(values: Map<String, Any?>): Logger

    public fun withError(err: Throwable): Logger

    /** Attaches the span's trace and span IDs so every subsequent line correlates with the trace. */
    public fun withSpan(span: Span): Logger

    public fun clone(): Logger
}

/** Applies [name] to [logger], mirroring `logging.NewNamedLogger`. */
public fun namedLogger(
    logger: Logger = NoopLogger,
    name: String,
): Logger = logger.withName(name)

/**
 * A logger that discards everything. Used as the safe default and in tests that don't assert on
 * output. Every `with*` returns the same singleton, since it carries no state.
 */
public object NoopLogger : Logger {
    override fun debug(msg: String) {}

    override fun info(msg: String) {}

    override fun warn(msg: String) {}

    override fun error(
        whatWasHappening: String,
        err: Throwable?,
    ) {}

    override fun withName(name: String): Logger = this

    override fun withValue(
        key: String,
        value: Any?,
    ): Logger = this

    override fun withValues(values: Map<String, Any?>): Logger = this

    override fun withError(err: Throwable): Logger = this

    override fun withSpan(span: Span): Logger = this

    override fun clone(): Logger = this
}
