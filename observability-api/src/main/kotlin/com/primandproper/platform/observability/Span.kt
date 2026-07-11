package com.primandproper.platform.observability

import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import io.opentelemetry.context.Context as OtelContext

/**
 * The active span type. Aliased straight to OpenTelemetry's [io.opentelemetry.api.trace.Span], the
 * same move platform-go makes with `type Span trace.Span`: don't re-abstract a standard.
 */
public typealias Span = io.opentelemetry.api.trace.Span

/**
 * Wraps this span as a coroutine context element that installs it as the current OpenTelemetry span
 * for the lifetime of the coroutine — surviving hops between dispatcher threads. This is the bridge
 * that lets trace context propagate implicitly through `suspend` calls instead of a threaded `ctx`.
 */
public fun Span.asCoroutineContextElement(): CoroutineContext = OtelContext.current().with(this).asContextElement()

/**
 * The idiomatic per-operation scope — the Android analog of Go's `ctx, op := o11y.Begin(ctx); defer
 * op.End()`. Starts a span named [name], runs [block] with the span installed in the coroutine
 * context (so nested operations parent correctly), records any thrown exception, and always ends the
 * span.
 *
 * ```
 * suspend fun add(groupId: String) = o11y.span("add") {
 *     set("group_id" to groupId)
 *     logger.debug("adding")
 * }
 * ```
 */
public suspend inline fun <T> Observer.span(
    name: String,
    crossinline block: suspend Operation.() -> T,
): T {
    val op = begin(name)
    return try {
        withContext(op.span.asCoroutineContextElement()) { op.block() }
    } catch (t: CancellationException) {
        // Coroutine cancellation is not a span error; never record it, just propagate.
        throw t
    } catch (t: Throwable) {
        op.acknowledge(t, "operation \"$name\" failed")
        throw t
    } finally {
        op.end()
    }
}

/**
 * The blocking counterpart of [span], for the non-coroutine call sites that still exist on Android
 * (synchronous callbacks, `WorkManager.doWork`, Java interop). Installs the span via a thread-local
 * scope rather than the coroutine context.
 */
public inline fun <T> Observer.spanBlocking(
    name: String,
    block: Operation.() -> T,
): T {
    val op = begin(name)
    return op.makeCurrent().use {
        try {
            op.block()
        } catch (t: CancellationException) {
            // Coroutine cancellation is not a span error; never record it, just propagate.
            throw t
        } catch (t: Throwable) {
            op.acknowledge(t, "operation \"$name\" failed")
            throw t
        } finally {
            op.end()
        }
    }
}
