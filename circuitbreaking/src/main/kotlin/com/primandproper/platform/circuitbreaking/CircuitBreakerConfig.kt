package com.primandproper.platform.circuitbreaking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_NAME: String = "UNKNOWN"

// platform-go's default `MinimumSampleThreshold` is 20 and its default `ErrorRate` is 100%, so the
// Go breaker trips only after 20 consecutive failures at the default settings. This port's
// consecutive-failure threshold reproduces that trip point at its default.
internal const val DEFAULT_FAILURE_THRESHOLD: Int = 20

internal val DEFAULT_RESET_TIMEOUT: Duration = 30.seconds
internal const val DEFAULT_HALF_OPEN_MAX_PROBES: Int = 1

/**
 * Configures a [CircuitBreaker] — the analog of platform-go's `circuitbreakingcfg.Config`. Built with
 * the mutable `{ }` block idiom used across this port (see `ObservabilityConfig`, `RetryConfig`)
 * rather than ozzo-validation's struct tags, since there is no env/JSON loader on Android to target.
 *
 * The Go config is expressed as a rolling error *rate* plus a minimum sample size (its
 * `rubyist/circuitbreaker` backing trips on `errorRate >= X% && samples >= N`). This port models the
 * equivalent lifecycle with the more common count-based knobs the porting plan called for:
 * [failureThreshold] consecutive failures trip the breaker, it stays open for [resetTimeout], then
 * admits up to [halfOpenMaxProbes] probes. [name] is retained for log correlation.
 */
public class CircuitBreakerConfig {
    /** Identifies the breaker in logs; the analog of Go's `Config.Name`. Required (defaults to `UNKNOWN`). */
    public var name: String = ""

    /** Consecutive failures in the closed state that trip the breaker. */
    public var failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD

    /** How long the breaker stays open before admitting a probe. */
    public var resetTimeout: Duration = DEFAULT_RESET_TIMEOUT

    /**
     * How many probe calls the half-open state admits, and equivalently how many must succeed before
     * the breaker closes. Any probe failure re-opens the breaker immediately.
     */
    public var halfOpenMaxProbes: Int = DEFAULT_HALF_OPEN_MAX_PROBES

    /**
     * Fills unset (zero/blank) fields with their defaults in place — the analog of Go's
     * `Config.EnsureDefaults`, which likewise fills only zero values (it does *not* clamp invalid
     * non-zero values, leaving those for [validate] to reject).
     */
    public fun ensureDefaults() {
        if (name.isBlank()) name = DEFAULT_NAME
        if (failureThreshold == 0) failureThreshold = DEFAULT_FAILURE_THRESHOLD
        if (resetTimeout == Duration.ZERO) resetTimeout = DEFAULT_RESET_TIMEOUT
        if (halfOpenMaxProbes == 0) halfOpenMaxProbes = DEFAULT_HALF_OPEN_MAX_PROBES
    }

    /**
     * Validates the config, throwing [IllegalArgumentException] on the first violation — the analog of
     * `Config.ValidateWithContext`. Manual `require()` checks stand in for ozzo-validation, which has
     * no Kotlin/Android equivalent in this port.
     */
    public fun validate() {
        require(name.isNotBlank()) { "circuitbreaking: name is required" }
        require(failureThreshold >= 1) {
            "circuitbreaking: failureThreshold must be >= 1, was $failureThreshold"
        }
        require(resetTimeout >= 1.milliseconds) {
            "circuitbreaking: resetTimeout must be >= 1ms, was $resetTimeout"
        }
        require(halfOpenMaxProbes >= 1) {
            "circuitbreaking: halfOpenMaxProbes must be >= 1, was $halfOpenMaxProbes"
        }
    }

    /** A field-for-field copy, so defaulting a breaker's config can't mutate the caller's instance. */
    internal fun copy(): CircuitBreakerConfig {
        val other = CircuitBreakerConfig()
        other.name = name
        other.failureThreshold = failureThreshold
        other.resetTimeout = resetTimeout
        other.halfOpenMaxProbes = halfOpenMaxProbes
        return other
    }
}
