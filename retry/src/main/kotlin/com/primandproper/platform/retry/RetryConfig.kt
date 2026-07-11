package com.primandproper.platform.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_MAX_ATTEMPTS: Int = 3
internal val DEFAULT_INITIAL_DELAY: Duration = 100.milliseconds
internal val DEFAULT_MAX_DELAY: Duration = 5.seconds
internal const val DEFAULT_MULTIPLIER: Double = 2.0

/**
 * Configures retry behavior — the analog of platform-go's `retry.Config`. An immutable data class
 * built with named arguments (`RetryConfig(maxAttempts = 5)`) rather than ozzo-validation's struct
 * tags, since there is no env/JSON loader on Android to target: defaults come from the constructor's
 * default arguments and the config is validated at construction in [init] (the analog of
 * `Config.ValidateWithContext`), so an invalid policy fails loudly at build time rather than being
 * silently clamped.
 *
 * [retryIf] extends platform-go's model: Go marks a single error non-retryable by wrapping it with
 * `Unretryable`; this adds an optional blanket predicate so a whole class of exceptions can be
 * excluded without wrapping each one. It defaults to "always retryable", which reproduces Go's
 * behavior exactly when left untouched.
 */
public data class RetryConfig(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
    val maxDelay: Duration = DEFAULT_MAX_DELAY,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val useJitter: Boolean = false,
    val retryIf: (Throwable) -> Boolean = { true },
) {
    init {
        require(maxAttempts >= 1) { "retry: maxAttempts must be >= 1, was $maxAttempts" }
        require(initialDelay >= 1.milliseconds) {
            "retry: initialDelay must be >= 1ms, was $initialDelay"
        }
        require(maxDelay >= 1.milliseconds) { "retry: maxDelay must be >= 1ms, was $maxDelay" }
        require(multiplier >= 1.0) { "retry: multiplier must be >= 1.0, was $multiplier" }
    }
}
