/*
 * Shared helpers for starting testcontainers with uniform retry behavior — the port of
 * platform-go's `testutils/containers` package. It exists so every container builder in the repo
 * can opt into the same backoff policy instead of each rolling its own.
 *
 * Container startup flakes for many non-deterministic reasons — Docker daemon cold starts, port
 * conflicts, image pull stalls, transient network blips — and a single attempt is too brittle for a
 * large integration test suite.
 */
package com.primandproper.platform.testutils.containers

import com.primandproper.platform.retry.ExponentialBackoffPolicy
import com.primandproper.platform.retry.RetryConfig
import org.junit.jupiter.api.Assumptions
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_MAX_ATTEMPTS: Int = 5
private val DEFAULT_INITIAL_DELAY = 1.seconds

/**
 * Parses the `RUN_CONTAINER_TESTS` flag the way [RunningTests] does — trimmed, case-insensitive,
 * `"true"` and nothing else. Split out from the val so the parsing is unit-testable without mutating
 * the process environment (which is what makes [RunningTests] itself effectively untestable).
 */
internal fun parseRunningTests(value: String?): Boolean = value?.trim()?.lowercase() == "true"

/**
 * Reports whether `RUN_CONTAINER_TESTS=true` is set in the environment. Container-backed tests
 * across the repo should gate on this (typically via [skipIfNotRunning]) so a default test run does
 * not require a Docker daemon. The variable is read once at class load, matching Go's package-init
 * read of the same env var.
 */
public val RunningTests: Boolean = parseRunningTests(System.getenv("RUN_CONTAINER_TESTS"))

/**
 * Skips (aborts) the current test when [RunningTests] is false — the analog of Go's
 * `SkipIfNotRunning(tb)`, which calls `tb.SkipNow()`. Implemented with JUnit5's
 * [Assumptions.assumeTrue], whose thrown `TestAbortedException` marks the test skipped rather than
 * failed; unlike Go there is no `TB` to thread through, since the assumption reads the ambient test
 * context itself. Requires a live Docker daemon to proceed past this line.
 */
public fun skipIfNotRunning(): Unit =
    Assumptions.assumeTrue(RunningTests) {
        "set RUN_CONTAINER_TESTS=true (and start Docker) to run container-backed tests"
    }

/**
 * Returns the [RetryConfig] used by [startWithRetry]: five attempts, a one-second initial delay, no
 * jitter — matching Go's `DefaultRetryConfig`. Callers that need bespoke retry behavior can start
 * from this and tweak individual fields before building their own policy.
 */
public fun defaultRetryConfig(): RetryConfig =
    RetryConfig().apply {
        maxAttempts = DEFAULT_MAX_ATTEMPTS
        initialDelay = DEFAULT_INITIAL_DELAY
        useJitter = false
    }

/**
 * Invokes [start] under [ExponentialBackoffPolicy] built from [defaultRetryConfig], so every
 * container builder in the repo gets the same backoff policy for free — the port of Go's
 * `StartWithRetry[C]`. Go threads a `context.Context`; here [start] is a suspend function, so
 * cancellation rides the coroutine context instead. On exhaustion the policy rethrows the last
 * failure (Kotlin's exception model replacing Go's returned `error`), so callers handle startup
 * failure with ordinary `try`/`assertX` rather than an explicit error return.
 */
public suspend fun <C> startWithRetry(start: suspend () -> C): C = ExponentialBackoffPolicy(defaultRetryConfig()).execute { start() }
