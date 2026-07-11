package com.primandproper.platform.retry

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration

/**
 * Executes a suspending operation with retry logic — the coroutine-native analog of platform-go's
 * `Policy` interface. `Execute(ctx, func(ctx) error) error` becomes a generic `execute(operation)`
 * that returns the operation's result (or throws): suspend functions already carry cancellation via
 * the coroutine context, so there is no separate `ctx` parameter, and Kotlin's exception-based error
 * model replaces the returned `error`.
 */
public interface Policy {
    /** Runs [operation], retrying per this policy's rules, and returns its result or throws. */
    public suspend fun <T> execute(operation: suspend () -> T): T
}

/** Runs [block] under [policy]. A thin top-level wrapper so call sites read as `retry(policy) { }`. */
public suspend fun <T> retry(
    policy: Policy,
    block: suspend () -> T,
): T = policy.execute(block)

/** A [Policy] that never retries — the analog of platform-go's `retry/noop.Policy`. */
public object NoopPolicy : Policy {
    override suspend fun <T> execute(operation: suspend () -> T): T = operation()
}

/**
 * Builds a [Policy] that retries with exponential backoff per [config] — the analog of
 * `NewExponentialBackoffPolicy`. [config] is an immutable value already defaulted and validated at its
 * own construction, matching Go's pass-by-value `Config`; build one with named arguments, e.g.
 * `ExponentialBackoffPolicy(RetryConfig(maxAttempts = 5))`.
 *
 * [logger] is optional and defaults to a noop: when supplied, each retried attempt logs the attempt
 * number, the delay before the next attempt, and the intermediate error at `warn`, so the failures
 * that would otherwise be swallowed (only the *last* error is thrown) leave a trail.
 */
public fun ExponentialBackoffPolicy(
    config: RetryConfig = RetryConfig(),
    logger: Logger = NoopLogger,
): Policy = ExponentialBackoffPolicyImpl(config, logger)

/**
 * Reports whether [error] must abort the retry loop rather than trigger another attempt: an
 * explicitly non-retryable error, or one [retryIf] rejects. [CancellationException] is handled
 * separately by [ExponentialBackoffPolicyImpl.execute] itself — it is always terminal and must
 * never reach this check, since it needs to propagate uncaught for structured concurrency to work.
 */
internal fun isTerminal(
    error: Throwable,
    retryIf: (Throwable) -> Boolean,
): Boolean = error is UnretryableException || !retryIf(error)

internal class ExponentialBackoffPolicyImpl(
    config: RetryConfig,
    logger: Logger,
) : Policy {
    private val maxAttempts: Int = config.maxAttempts
    private val retryIf: (Throwable) -> Boolean = config.retryIf
    private val initialDelay: Duration = config.initialDelay
    private val maxDelay: Duration = config.maxDelay
    private val multiplier: Double = config.multiplier
    private val useJitter: Boolean = config.useJitter
    private val log: Logger = logger

    override suspend fun <T> execute(operation: suspend () -> T): T {
        // A fresh, loop-local backoff per invocation — mirroring Go's `delay` declared inside
        // `Execute`. Keeping the escalating state off the long-lived policy object is what lets a
        // DI-shared policy start every call from `initialDelay` again, and stops concurrent
        // `execute()` calls from racing on a shared mutable field.
        val backoff =
            Backoff(
                initialDelay = initialDelay,
                maxDelay = maxDelay,
                multiplier = multiplier,
                useJitter = useJitter,
            )
        var lastError: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            // Equivalent of Go's `select { case <-ctx.Done(): ... }` at the top of the loop: bail
            // before even attempting the operation if the coroutine was already cancelled.
            currentCoroutineContext().ensureActive()

            lastError =
                try {
                    return operation()
                } catch (cancellation: CancellationException) {
                    // Never swallow cancellation into the retry accounting — it must propagate
                    // immediately, on the first occurrence, exactly like Go's isTerminal(ctx.Canceled).
                    throw cancellation
                } catch (error: Throwable) {
                    error
                }

            if (isTerminal(lastError, retryIf) || attempt == maxAttempts - 1) {
                throw lastError
            }

            val nextDelay = backoff.next()
            log
                .withValue("attempt", attempt + 1)
                .withValue("max_attempts", maxAttempts)
                .withValue("delay", nextDelay)
                .withError(lastError)
                .warn("retry: attempt failed, retrying after delay")

            // delay() itself is a suspension point, so a cancellation during the sleep propagates
            // immediately too — the same race `select { case <-ctx.Done(): ... case <-time.After(...): }`
            // resolves in Go.
            delay(nextDelay)
        }

        throw checkNotNull(lastError) { "retry: unreachable — maxAttempts must be >= 1" }
    }
}
