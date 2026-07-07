package com.primandproper.platform.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

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
 * `NewExponentialBackoffPolicy`. [config] is copied and defaulted at construction time
 * ([RetryConfig.ensureDefaults]), so mutating the original afterward has no effect, matching Go's
 * pass-by-value `Config`.
 */
public fun ExponentialBackoffPolicy(config: RetryConfig): Policy {
    val normalized = config.copy().apply { ensureDefaults() }
    return ExponentialBackoffPolicyImpl(normalized)
}

/** Builds a [Policy] from an inline config block: `ExponentialBackoffPolicy { maxAttempts = 5 }`. */
public fun ExponentialBackoffPolicy(configure: RetryConfig.() -> Unit): Policy = ExponentialBackoffPolicy(RetryConfig().apply(configure))

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

internal class ExponentialBackoffPolicyImpl(config: RetryConfig) : Policy {
    private val maxAttempts: Int = config.maxAttempts
    private val retryIf: (Throwable) -> Boolean = config.retryIf
    private val backoff =
        Backoff(
            initialDelay = config.initialDelay,
            maxDelay = config.maxDelay,
            multiplier = config.multiplier,
            useJitter = config.useJitter,
        )

    override suspend fun <T> execute(operation: suspend () -> T): T {
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

            // delay() itself is a suspension point, so a cancellation during the sleep propagates
            // immediately too — the same race `select { case <-ctx.Done(): ... case <-time.After(...): }`
            // resolves in Go.
            delay(backoff.next())
        }

        throw checkNotNull(lastError) { "retry: unreachable — maxAttempts must be >= 1" }
    }
}
