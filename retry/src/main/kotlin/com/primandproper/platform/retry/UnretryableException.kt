package com.primandproper.platform.retry

/**
 * Thrown (or passed to [unretryable]) to mark an exception as one a [Policy] must not retry — the
 * analog of platform-go's `ErrUnretryable`/`Unretryable`. The original exception is preserved as
 * [Throwable.cause], so `cause` chains and `is`/`as` checks against it still work.
 *
 * Unlike Go, where retrying is opt-out per error via wrapping, [CancellationException] is *always*
 * terminal regardless of this type — cancellation must never be swallowed by a retry loop.
 */
public class UnretryableException(cause: Throwable) : RuntimeException(cause.message, cause)

/** Wraps [cause] so a [Policy] stops retrying on it instead of exhausting the remaining attempts. */
public fun unretryable(cause: Throwable): UnretryableException = UnretryableException(cause)
