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
 * [ensureDefaults] is `EnsureDefaults`; [validate] is `ValidateWithContext` — reporting
 * misconfiguration by throwing rather than returning an error, exactly as `ObservabilityConfig`.
 *
 * Build one fluently with the `HttpClientConfig { }` DSL.
 */
public class HttpClientConfig {
    /** Whole-call timeout. Zero means "apply [DEFAULT_TIMEOUT] in [ensureDefaults]". */
    public var timeout: Duration = Duration.ZERO

    /** Connection-establishment timeout. Zero defaults to [timeout] in [ensureDefaults]. */
    public var connectTimeout: Duration = Duration.ZERO

    public var maxIdleConns: Int = 0
    public var maxIdleConnsPerHost: Int = 0

    /** When true, the backend installs its OpenTelemetry instrumentation so calls join the trace. */
    public var enableTracing: Boolean = false

    /**
     * Optional retry seam. Null (the default) means one attempt, no retries. The real backoff/jitter
     * policy is the separately-ported `retry` package — wire an implementation in here rather than
     * baking a policy into the client. See [executeWithRetries].
     */
    public var retryHook: RetryHook? = null

    /** Sets default values for zero fields, mirroring `EnsureDefaults`. */
    public fun ensureDefaults() {
        if (timeout == Duration.ZERO) timeout = DEFAULT_TIMEOUT
        if (connectTimeout == Duration.ZERO) connectTimeout = timeout
        if (maxIdleConns == 0) maxIdleConns = DEFAULT_MAX_IDLE_CONNS
        if (maxIdleConnsPerHost == 0) maxIdleConnsPerHost = DEFAULT_MAX_IDLE_CONNS_PER_HOST
    }

    /** Validates the config, throwing [IllegalArgumentException] on the first problem. */
    public fun validate() {
        require(timeout >= 1.milliseconds) { "httpclient: timeout must be at least 1ms, was $timeout" }
        require(connectTimeout >= 1.milliseconds) {
            "httpclient: connectTimeout must be at least 1ms, was $connectTimeout"
        }
        require(maxIdleConns >= 1) { "httpclient: maxIdleConns must be at least 1, was $maxIdleConns" }
        require(maxIdleConnsPerHost >= 1) {
            "httpclient: maxIdleConnsPerHost must be at least 1, was $maxIdleConnsPerHost"
        }
    }
}

/** DSL entry point, mirroring `Observability { }` / `Observer(...)` factory style. */
public fun HttpClientConfig(block: HttpClientConfig.() -> Unit): HttpClientConfig = HttpClientConfig().apply(block)
