package com.primandproper.platform.observability

import io.opentelemetry.api.trace.StatusCode

/**
 * The per-call observability bag returned by [Observer.begin]. A value recorded via [set] lands on
 * both the active span and the running logger, so a value selected once is available to either
 * pillar later; [spanOnly] / [logOnly] are the escape hatches. Mirrors platform-go's `Operation`.
 *
 * It is an interface so a recording double can hand back an [Operation] a test reads values off of
 * (see the `observability-testing` module).
 */
public interface Operation {
    public fun set(key: String, value: Any?): Operation
    public fun set(vararg pairs: Pair<String, Any?>): Operation
    public fun spanOnly(key: String, value: Any?): Operation
    public fun logOnly(key: String, value: Any?): Operation

    /** The running logger, carrying every [set]/[logOnly] value and the span link. */
    public val logger: Logger

    /** The active span, for independent use. */
    public val span: Span

    /** Records [err] on the span and logs it, then returns it for `throw op.error(e, "...")`. */
    public fun <E : Throwable> error(err: E, message: String): E

    /** Records [err] on the span and logs it, without rethrowing. Mirrors Go's `Acknowledge`. */
    public fun acknowledge(err: Throwable, message: String)

    /** Installs the span as the current OTel context on this thread; close to restore. Used by [spanBlocking]. */
    public fun makeCurrent(): AutoCloseable

    /** Ends the span. */
    public fun end()
}

internal class DefaultOperation(
    override val span: Span,
    spanLinkedLogger: Logger,
) : Operation {
    private var current: Logger = spanLinkedLogger
    override val logger: Logger get() = current

    override fun set(key: String, value: Any?): Operation {
        span.setAttributeAny(key, value)
        current = current.withValue(key, value)
        return this
    }

    override fun set(vararg pairs: Pair<String, Any?>): Operation {
        for ((k, v) in pairs) set(k, v)
        return this
    }

    override fun spanOnly(key: String, value: Any?): Operation {
        span.setAttributeAny(key, value)
        return this
    }

    override fun logOnly(key: String, value: Any?): Operation {
        current = current.withValue(key, value)
        return this
    }

    override fun <E : Throwable> error(err: E, message: String): E {
        recordAndLog(err, message)
        return err
    }

    override fun acknowledge(err: Throwable, message: String) {
        recordAndLog(err, message)
    }

    private fun recordAndLog(err: Throwable, message: String) {
        if (span.isRecording) {
            span.recordException(err)
            span.setStatus(StatusCode.ERROR, message)
        }
        current.error(message, err)
    }

    override fun makeCurrent(): AutoCloseable = span.makeCurrent()

    override fun end() {
        span.end()
    }
}

/**
 * Attaches an arbitrary value to a span as a typed attribute, widening as OpenTelemetry requires and
 * falling back to `toString()` for anything exotic. No-ops on a non-recording (noop/sampled-out)
 * span, so callers never branch.
 */
internal fun Span.setAttributeAny(key: String, value: Any?) {
    if (!isRecording) return
    when (value) {
        null -> setAttribute(key, "null")
        is String -> setAttribute(key, value)
        is Boolean -> setAttribute(key, value)
        is Int -> setAttribute(key, value.toLong())
        is Long -> setAttribute(key, value)
        is Short -> setAttribute(key, value.toLong())
        is Byte -> setAttribute(key, value.toLong())
        is Double -> setAttribute(key, value)
        is Float -> setAttribute(key, value.toDouble())
        else -> setAttribute(key, value.toString())
    }
}
