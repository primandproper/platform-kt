package com.primandproper.platform.errors

/**
 * Platform-level errors, mirroring platform-go's `errors` package.
 *
 * platform-go re-exports `cockroachdb/errors` for construction/wrapping and relies on the std
 * `errors.Is`/`errors.As`/`errors.Unwrap` for inspection. On the JVM the language already gives us
 * exceptions with a `cause` chain, so the port maps those Go idioms onto plain [Throwable]s:
 *
 * - `New`/`Newf`/`Errorf` → [newError] (build the message with a Kotlin string template).
 * - `Wrap`/`Wrapf`        → [wrap] (takes a non-null cause; interpolate the message inline).
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

/**
 * Creates a new error with [message]. Mirrors `errors.New` (and `errors.Newf`/`errors.Errorf` —
 * build the formatted message with a Kotlin string template at the call site).
 */
public fun newError(message: String): PlatformException = PlatformException(message)

/**
 * Wraps [cause] with a higher-level [message], preserving the cause chain so [isError]/[asError]
 * (and JVM stack traces) still see the original. The rendered message is `"<message>: <cause
 * message>"`, as cockroachdb/errors produces.
 *
 * Unlike Go's `errors.Wrap` (which returns `nil` for a `nil` cause), [cause] is non-null: Kotlin's
 * type system already lets a caller decide whether to wrap, so this always returns a real exception
 * and the caller is spared the `!!`/`?: fallback` dance the nullable Go shape forced.
 */
public fun wrap(
    cause: Throwable,
    message: String,
): PlatformException = PlatformException("$message: ${cause.message}", cause)

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
 *
 * Prefer the type-aware [isError] overload (or a plain `is`/[asError] check) for the platform error
 * *types* below: those are matched by class, not by identity, so an instance copied by coroutine
 * stack-trace recovery (which breaks `===`) still matches. This instance-based overload remains for
 * genuine value sentinels a caller may still hold.
 */
public fun isError(
    error: Throwable?,
    target: Throwable?,
): Boolean {
    if (target == null) return error == null
    return anyInChain(error) { it === target || it == target }
}

/**
 * Reports whether [error], or anything in its cause chain (descending into [JoinedException]), is a
 * [T]. The type-aware [errors.Is] analog: it matches by class, so it is robust to the exception being
 * wrapped or copied (coroutine stack-trace recovery copies exceptions, breaking `===`). Use it in
 * place of identity checks against the platform error classes, e.g. `isError<CircuitBrokenException>(e)`.
 */
public inline fun <reified T : Throwable> isError(error: Throwable?): Boolean = anyInChain(error) { it is T }

/**
 * Walks [error]'s cause chain (descending into [JoinedException] members) and reports whether any
 * throwable satisfies [predicate]. The shared traversal behind both [isError] overloads.
 */
public fun anyInChain(
    error: Throwable?,
    predicate: (Throwable) -> Boolean,
): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (predicate(current)) return true
        if (current is JoinedException && current.errors.any { anyInChain(it, predicate) }) return true
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

// Common platform error types. In platform-go these are `var Err… = errors.New(…)` sentinels matched
// via errors.Is; here they are exception CLASSES thrown fresh at each site and matched by type (via a
// plain `is`, [asError], or the type-aware [isError] overload) — never shared singleton instances.
//
// The nil-input sentinels (Go's `ErrNilInputParameter` / `ErrNilInputProvided`) are intentionally
// dropped: Kotlin's non-null types already forbid what they modelled. Sites that need an argument
// check use `require`/`requireNotNull` (an IllegalArgumentException) instead.

/** Thrown when a required input parameter is empty. */
public class EmptyInputParameterException : PlatformException("provided input parameter is empty")

/** Thrown when a required ID is passed in empty. */
public class InvalidIDProvidedException : PlatformException("required ID provided is empty")

/** Thrown when a required input is passed in empty. */
public class EmptyInputProvidedException : PlatformException("input provided is empty")
