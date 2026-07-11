package com.primandproper.platform.retry

import com.primandproper.platform.observability.Level
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Span

/** A single line captured by [RecordingLogger], with the context values and error bound when emitted. */
internal data class RecordedLine(
    val level: Level,
    val message: String,
    val values: Map<String, Any?>,
    val error: Throwable?,
)

/**
 * A [Logger] test double that records emitted lines (plus the fluent context and error bound to them),
 * so a test can assert what the retry loop logged without a live backend. `with*` returns a new
 * instance sharing the same sink, mirroring the immutable-context contract of the real [Logger].
 */
internal class RecordingLogger private constructor(
    private val sink: MutableList<RecordedLine>,
    private val values: Map<String, Any?>,
    private val error: Throwable?,
) : Logger {
    constructor() : this(mutableListOf(), emptyMap(), null)

    val lines: List<RecordedLine> get() = sink.toList()

    override fun debug(msg: String) {
        sink += RecordedLine(Level.DEBUG, msg, values, error)
    }

    override fun info(msg: String) {
        sink += RecordedLine(Level.INFO, msg, values, error)
    }

    override fun warn(msg: String) {
        sink += RecordedLine(Level.WARN, msg, values, error)
    }

    override fun error(
        whatWasHappening: String,
        err: Throwable?,
    ) {
        sink += RecordedLine(Level.ERROR, whatWasHappening, values, err ?: error)
    }

    override fun withName(name: String): Logger = this

    override fun withValue(
        key: String,
        value: Any?,
    ): Logger = RecordingLogger(sink, values + (key to value), error)

    override fun withValues(values: Map<String, Any?>): Logger = RecordingLogger(sink, this.values + values, error)

    override fun withError(err: Throwable): Logger = RecordingLogger(sink, values, err)

    override fun withSpan(span: Span): Logger = this

    override fun clone(): Logger = this
}
