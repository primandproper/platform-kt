package com.primandproper.platform.circuitbreaking

import com.primandproper.platform.observability.Level
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Span

/** A single line captured by [RecordingLogger], with the context values bound when it was emitted. */
internal data class RecordedLine(
    val level: Level,
    val message: String,
    val values: Map<String, Any?>,
)

/**
 * A [Logger] test double that records emitted lines (and the fluent context bound to them), so a test
 * can assert what a unit logged without a live backend. `with*` returns a new instance sharing the
 * same sink, mirroring the immutable-context contract of the real [Logger].
 */
internal class RecordingLogger private constructor(
    private val sink: MutableList<RecordedLine>,
    private val values: Map<String, Any?>,
) : Logger {
    constructor() : this(mutableListOf(), emptyMap())

    val lines: List<RecordedLine> get() = sink.toList()

    override fun debug(msg: String) {
        sink += RecordedLine(Level.DEBUG, msg, values)
    }

    override fun info(msg: String) {
        sink += RecordedLine(Level.INFO, msg, values)
    }

    override fun warn(msg: String) {
        sink += RecordedLine(Level.WARN, msg, values)
    }

    override fun error(
        whatWasHappening: String,
        err: Throwable?,
    ) {
        sink += RecordedLine(Level.ERROR, whatWasHappening, values)
    }

    override fun withName(name: String): Logger = this

    override fun withValue(
        key: String,
        value: Any?,
    ): Logger = RecordingLogger(sink, values + (key to value))

    override fun withValues(values: Map<String, Any?>): Logger = RecordingLogger(sink, this.values + values)

    override fun withError(err: Throwable): Logger = this

    override fun withSpan(span: Span): Logger = this

    override fun clone(): Logger = this
}
