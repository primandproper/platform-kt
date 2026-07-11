package com.primandproper.platform.httpclient

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Default per-call timeout, mirroring platform-go's `defaultTimeout`. */
public val DEFAULT_TIMEOUT: Duration = 10.seconds

/** Default idle-connection ceiling, mirroring platform-go's `defaultMaxIdleConns`. */
public const val DEFAULT_MAX_IDLE_CONNS: Int = 100

/** Default per-host idle-connection ceiling, mirroring `defaultMaxIdleConnsPerHost`. */
public const val DEFAULT_MAX_IDLE_CONNS_PER_HOST: Int = 100

/**
 * Configures a backend HTTP client. Port of platform-go's `httpclient.Config`, with two Kotlin-native
 * additions: [connectTimeout] (Go folds this into the dialer timeout) and the [retryHook] seam.
 *
 * Immutable: defaults are supplied by the constructor's default arguments (with the timeouts using a
 * `null` sentinel resolved on read by [timeout]/[connectTimeout], since `connectTimeout` falls back to
 * the resolved `timeout`), and the config is validated at construction in [init] rather than in a
 * separate `EnsureDefaults`/`ValidateWithContext` phase.
 *
 * Build one with named arguments: `HttpClientConfig(timeout = 5.seconds)`.
 *
 * @param timeout whole-call timeout; `null` (the default) resolves to [DEFAULT_TIMEOUT].
 * @param connectTimeout connection-establishment timeout; `null` (the default) resolves to [timeout].
 * @param enableTracing whether the backend installs its OpenTelemetry instrumentation so calls join
 *   the trace and inject `traceparent`. `null` (the default) means "auto": the client factories enable
 *   tracing when a real (non-noop) `OpenTelemetry` is supplied, and leave it off for the noop default.
 *   Set it explicitly to force tracing on or off regardless of the supplied `OpenTelemetry`.
 * @param retryHook optional retry seam. `null` (the default) means one attempt, no retries. The real
 *   backoff/jitter policy is the separately-ported `retry` package — wire an implementation in here
 *   rather than baking a policy into the client. See [executeWithRetries].
 */
public data class HttpClientConfig(
    val timeout: Duration? = null,
    val connectTimeout: Duration? = null,
    val maxIdleConns: Int = DEFAULT_MAX_IDLE_CONNS,
    val maxIdleConnsPerHost: Int = DEFAULT_MAX_IDLE_CONNS_PER_HOST,
    val enableTracing: Boolean? = null,
    val retryHook: RetryHook? = null,
) {
    init {
        timeout?.let { require(it >= 1.milliseconds) { "httpclient: timeout must be at least 1ms, was $it" } }
        connectTimeout?.let {
            require(it >= 1.milliseconds) { "httpclient: connectTimeout must be at least 1ms, was $it" }
        }
        require(maxIdleConns >= 1) { "httpclient: maxIdleConns must be at least 1, was $maxIdleConns" }
        require(maxIdleConnsPerHost >= 1) {
            "httpclient: maxIdleConnsPerHost must be at least 1, was $maxIdleConnsPerHost"
        }
    }

    /** The whole-call timeout, or [DEFAULT_TIMEOUT] when unset. */
    public fun timeout(): Duration = timeout ?: DEFAULT_TIMEOUT

    /** The connection-establishment timeout, or the resolved [timeout] when unset. */
    public fun connectTimeout(): Duration = connectTimeout ?: timeout()
}
