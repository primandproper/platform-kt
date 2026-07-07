package com.primandproper.platform.observability.logcat

import android.util.Log
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Level
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Span

/**
 * A [Logger] that writes to Logcat via [android.util.Log] — the dev-facing backend, equivalent to
 * platform-go's slog logger. Immutable: every `with*` returns a new instance carrying the added
 * context, so it composes safely across threads (hence [clone] just returns `this`).
 *
 * Accumulated values are rendered as a trailing `{k=v, ...}` block; trace/span IDs added via
 * [withSpan] ride along the same way, so Logcat lines correlate with the trace.
 */
public class LogcatLogger private constructor(
    private val tag: String,
    private val minLevel: Level,
    private val values: Map<String, Any?>,
    private val throwable: Throwable?,
) : Logger {
    public constructor(tag: String = "platform", minLevel: Level = Level.INFO) :
        this(tag, minLevel, emptyMap(), null)

    private fun enabled(level: Level): Boolean = level.ordinal >= minLevel.ordinal

    private fun render(msg: String): String =
        if (values.isEmpty()) {
            msg
        } else {
            buildString {
                append(msg)
                append(" {")
                values.entries.joinTo(this, separator = ", ") { (k, v) -> "$k=$v" }
                append('}')
            }
        }

    override fun debug(msg: String) {
        if (enabled(Level.DEBUG)) Log.d(tag, render(msg))
    }

    override fun info(msg: String) {
        if (enabled(Level.INFO)) Log.i(tag, render(msg))
    }

    override fun warn(msg: String) {
        if (enabled(Level.WARN)) Log.w(tag, render(msg))
    }

    override fun error(
        whatWasHappening: String,
        err: Throwable?,
    ) {
        if (enabled(Level.ERROR)) Log.e(tag, render(whatWasHappening), err ?: throwable)
    }

    override fun withName(name: String): Logger = copy(tag = name)

    override fun withValue(
        key: String,
        value: Any?,
    ): Logger = copy(values = values + (key to value))

    override fun withValues(values: Map<String, Any?>): Logger = copy(values = this.values + values)

    override fun withError(err: Throwable): Logger = copy(throwable = err)

    override fun withSpan(span: Span): Logger {
        val ctx = span.spanContext
        if (!ctx.isValid) return this
        return copy(values = values + mapOf(Keys.TRACE_ID to ctx.traceId, Keys.SPAN_ID to ctx.spanId))
    }

    override fun clone(): Logger = this

    private fun copy(
        tag: String = this.tag,
        minLevel: Level = this.minLevel,
        values: Map<String, Any?> = this.values,
        throwable: Throwable? = this.throwable,
    ): LogcatLogger = LogcatLogger(tag, minLevel, values, throwable)
}
