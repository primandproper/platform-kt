package com.primandproper.platform.errors

/**
 * Platform-level errors, mirroring platform-go's `errors` package.
 *
 * platform-go re-exports `cockroachdb/errors` for construction/wrapping and relies on the std
 * `errors.Is`/`errors.As`/`errors.Unwrap` for inspection. On the JVM the language already gives us
 * exceptions with a `cause` chain, so the port maps those Go idioms onto plain [Throwable]s:
 *
 * - `New`/`Newf`/`Errorf` → [newError] / [newErrorf].
 * - `Wrap`/`Wrapf`        → [wrap] / [wrapf] (both return `null` for a `null` cause, like Go).
 * - `Join`               → [joinErrors].
 * - `errors.Is`          → [isError] (walks the cause chain, understands [joinErrors]).
 * - `errors.As`          → [asError] (finds the first cause of a given type).
 *
 * The `EncodeError`/`DecodeError` wire helpers from cockroachdb/errors are intentionally omitted:
 * they exist to ship Go errors across a gRPC boundary and have no JVM analog here.
 */
public open class PlatformException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Creates a new error with [message]. Mirrors `errors.New`. */
public fun newError(message: String): PlatformException = PlatformException(message)

/**
 * Creates a new error whose message is [format] rendered with [args] via [String.format].
 * Mirrors `errors.Newf` / `errors.Errorf`.
 */
public fun newErrorf(
    format: String,
    vararg args: Any?,
): PlatformException = PlatformException(format.format(*args))

/**
 * Wraps [cause] with a higher-level [message], preserving the cause chain so [isError]/[asError]
 * (and JVM stack traces) still see the original. Returns `null` when [cause] is `null`, matching
 * `errors.Wrap(nil, ...)` returning `nil`. The rendered message is `"<message>: <cause message>"`,
 * as cockroachdb/errors produces.
 */
public fun wrap(
    cause: Throwable?,
    message: String,
): PlatformException? = cause?.let { PlatformException("$message: ${it.message}", it) }

/** [wrap] with a formatted message. Mirrors `errors.Wrapf`. */
public fun wrapf(
    cause: Throwable?,
    format: String,
    vararg args: Any?,
): PlatformException? = wrap(cause, format.format(*args))

/**
 * Combines several errors into one, mirroring `errors.Join`. `null` entries are dropped; the result
 * is `null` when nothing remains and the single error itself when only one remains. [isError] knows
 * how to descend into the combined set.
 */
public fun joinErrors(vararg errors: Throwable?): Throwable? {
    val present = errors.filterNotNull()
    return when (present.size) {
        0 -> null
        1 -> present.first()
        else -> JoinedException(present)
    }
}

/** Carrier produced by [joinErrors] when more than one error is combined. */
public class JoinedException internal constructor(
    public val errors: List<Throwable>,
) : PlatformException(errors.joinToString("\n") { it.message ?: it.toString() })

/**
 * Reports whether [error], or anything in its cause chain, matches [target]. This is the [errors.Is]
 * analog: it compares by reference and by `equals`, and descends into [JoinedException] members.
 * Because the platform sentinels below are singletons, `isError(err, ErrNilInputParameter)` behaves
 * exactly like the Go `errors.Is(err, ErrNilInputParameter)` sentinel check.
 */
public fun isError(
    error: Throwable?,
    target: Throwable?,
): Boolean {
    if (target == null) return error == null
    var current: Throwable? = error
    while (current != null) {
        if (current === target || current == target) return true
        if (current is JoinedException && current.errors.any { isError(it, target) }) return true
        current = current.cause
    }
    return false
}

/**
 * Returns the first error in [error]'s cause chain that is a [T], or `null`. The [errors.As] analog;
 * prefer a plain `is` check when you already hold the leaf error.
 */
public inline fun <reified T : Throwable> asError(error: Throwable?): T? {
    var current: Throwable? = error
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

// Common platform sentinels. In platform-go these are `var Err… = errors.New(…)` values matched via
// errors.Is; here they are singleton exceptions matched via [isError] (identity through the chain).

/** Returned when an input parameter is `null`. */
public val ErrNilInputParameter: PlatformException = PlatformException("provided input parameter is nil")

/** Returned when an input parameter is empty. */
public val ErrEmptyInputParameter: PlatformException = PlatformException("provided input parameter is empty")

/** Indicates `null` input was provided in an unacceptable context. */
public val ErrNilInputProvided: PlatformException = PlatformException("nil input provided")

/** Indicates a required ID was passed in empty. */
public val ErrInvalidIDProvided: PlatformException = PlatformException("required ID provided is empty")

/** Indicates a required input was passed in empty. */
public val ErrEmptyInputProvided: PlatformException = PlatformException("input provided is empty")
