package com.primandproper.platform.circuitbreaking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// platform-go's default `MinimumSampleThreshold` is 20 and its default `ErrorRate` is 100%, so the
// Go breaker trips only after 20 consecutive failures at the default settings. This port's
// consecutive-failure threshold reproduces that trip point at its default.
internal const val DEFAULT_FAILURE_THRESHOLD: Int = 20

internal val DEFAULT_RESET_TIMEOUT: Duration = 30.seconds
internal const val DEFAULT_HALF_OPEN_MAX_PROBES: Int = 1

/**
 * Configures a [CircuitBreaker] — the analog of platform-go's `circuitbreakingcfg.Config`. An
 * immutable data class built with named arguments (`CircuitBreakerConfig(name = "svc")`) rather than
 * ozzo-validation's struct tags, since there is no env/JSON loader on Android to target: the numeric
 * knobs default via the constructor's default arguments and the config is validated at construction in
 * [init] (the analog of `Config.ValidateWithContext`).
 *
 * The Go config is expressed as a rolling error *rate* plus a minimum sample size (its
 * `rubyist/circuitbreaker` backing trips on `errorRate >= X% && samples >= N`). This port models the
 * equivalent lifecycle with the more common count-based knobs the porting plan called for:
 * [failureThreshold] consecutive failures trip the breaker, it stays open for [resetTimeout], then
 * admits up to [halfOpenMaxProbes] probes.
 *
 * @param name identifies the breaker in logs; the analog of Go's `Config.Name`. Required and non-blank
 *   (no `UNKNOWN` fallback — the earlier default masked a genuinely required field).
 * @param failureThreshold consecutive failures in the closed state that trip the breaker.
 * @param resetTimeout how long the breaker stays open before admitting a probe.
 * @param halfOpenMaxProbes how many probe calls the half-open state admits, and equivalently how many
 *   must succeed before the breaker closes. Any probe failure re-opens the breaker immediately.
 */
public data class CircuitBreakerConfig(
    val name: String,
    val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    val resetTimeout: Duration = DEFAULT_RESET_TIMEOUT,
    val halfOpenMaxProbes: Int = DEFAULT_HALF_OPEN_MAX_PROBES,
) {
    init {
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
}
