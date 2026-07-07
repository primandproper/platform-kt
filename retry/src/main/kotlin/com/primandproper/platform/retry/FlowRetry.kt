package com.primandproper.platform.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/**
 * Re-collects this [Flow] from the start on failure, using the same exponential-backoff-with-jitter
 * schedule as [ExponentialBackoffPolicy] — a `Flow`-native counterpart, since `kotlinx.coroutines.flow`
 * already ships [retryWhen] and platform-go has no stream type to port this from.
 *
 * As with [ExponentialBackoffPolicyImpl.execute], [CancellationException] always propagates and a
 * failed [RetryConfig.retryIf] check or an [UnretryableException] always stops retrying.
 */
public fun <T> Flow<T>.retryWithPolicy(config: RetryConfig): Flow<T> {
    val normalized = config.copy().apply { ensureDefaults() }
    val retryIf = normalized.retryIf
    val backoff =
        Backoff(
            initialDelay = normalized.initialDelay,
            maxDelay = normalized.maxDelay,
            multiplier = normalized.multiplier,
            useJitter = normalized.useJitter,
        )

    return retryWhen { cause, attempt ->
        if (cause is CancellationException) throw cause

        val exhausted = attempt >= normalized.maxAttempts - 1
        if (isTerminal(cause, retryIf) || exhausted) {
            false
        } else {
            delay(backoff.next())
            true
        }
    }
}

/** [retryWithPolicy] built from an inline config block: `flow.retryWithPolicy { maxAttempts = 5 }`. */
public fun <T> Flow<T>.retryWithPolicy(configure: RetryConfig.() -> Unit): Flow<T> = retryWithPolicy(RetryConfig().apply(configure))
