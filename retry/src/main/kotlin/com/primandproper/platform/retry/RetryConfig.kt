package com.primandproper.platform.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_MAX_ATTEMPTS: Int = 3
internal val DEFAULT_INITIAL_DELAY: Duration = 100.milliseconds
internal val DEFAULT_MAX_DELAY: Duration = 5.seconds
internal const val DEFAULT_MULTIPLIER: Double = 2.0

/**
 * Configures retry behavior — the analog of platform-go's `retry.Config`. Built with the mutable
 * `{ }` block idiom used across this port (see `ObservabilityConfig`) rather than ozzo-validation's
 * struct tags, since there is no env/JSON loader on Android to target.
 *
 * [retryIf] extends platform-go's model: Go marks a single error non-retryable by wrapping it with
 * `Unretryable`; this adds an optional blanket predicate so a whole class of exceptions can be
 * excluded without wrapping each one. It defaults to "always retryable", which reproduces Go's
 * behavior exactly when left untouched.
 */
public class RetryConfig {
    public var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    public var initialDelay: Duration = DEFAULT_INITIAL_DELAY
    public var maxDelay: Duration = DEFAULT_MAX_DELAY
    public var multiplier: Double = DEFAULT_MULTIPLIER
    public var useJitter: Boolean = false
    public var retryIf: (Throwable) -> Boolean = { true }

    /**
     * Clamps zero/invalid fields to their defaults in place — the analog of `Config.EnsureDefaults`.
     * Clamping (rather than merely zero-checking) means a negative delay or a sub-1.0 multiplier —
     * either of which would produce a pathological policy — can't slip through, since policy
     * construction has no error return to reject it.
     */
    public fun ensureDefaults() {
        if (maxAttempts <= 0) maxAttempts = DEFAULT_MAX_ATTEMPTS
        if (initialDelay <= Duration.ZERO) initialDelay = DEFAULT_INITIAL_DELAY
        if (maxDelay <= Duration.ZERO) maxDelay = DEFAULT_MAX_DELAY
        if (multiplier < 1.0) multiplier = DEFAULT_MULTIPLIER
    }

    /**
     * Validates the config, throwing [IllegalArgumentException] on the first violation — the analog
     * of `Config.ValidateWithContext`. Manual `require()` checks stand in for ozzo-validation, which
     * has no Kotlin/Android equivalent in this port.
     */
    public fun validate() {
        require(maxAttempts >= 1) { "retry: maxAttempts must be >= 1, was $maxAttempts" }
        require(initialDelay >= 1.milliseconds) {
            "retry: initialDelay must be >= 1ms, was $initialDelay"
        }
        require(maxDelay >= 1.milliseconds) { "retry: maxDelay must be >= 1ms, was $maxDelay" }
        require(multiplier >= 1.0) { "retry: multiplier must be >= 1.0, was $multiplier" }
    }

    /** A field-for-field copy, so normalizing a policy's config can't mutate the caller's instance. */
    internal fun copy(): RetryConfig {
        val other = RetryConfig()
        other.maxAttempts = maxAttempts
        other.initialDelay = initialDelay
        other.maxDelay = maxDelay
        other.multiplier = multiplier
        other.useJitter = useJitter
        other.retryIf = retryIf
        return other
    }
}
