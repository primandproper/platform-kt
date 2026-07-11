package com.primandproper.platform.retry

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen

/**
 * Re-collects this [Flow] from the start on failure, using the same exponential-backoff-with-jitter
 * schedule as [ExponentialBackoffPolicy] — a `Flow`-native counterpart, since `kotlinx.coroutines.flow`
 * already ships [retryWhen] and platform-go has no stream type to port this from.
 *
 * As with [ExponentialBackoffPolicyImpl.execute], [CancellationException] always propagates and a
 * failed [RetryConfig.retryIf] check or an [UnretryableException] always stops retrying.
 *
 * [logger] is optional and defaults to a noop: when supplied, each retried collection logs the
 * attempt number, the delay before the next attempt, and the intermediate error at `warn`.
 */
public fun <T> Flow<T>.retryWithPolicy(
    config: RetryConfig = RetryConfig(),
    logger: Logger = NoopLogger,
): Flow<T> {
    val retryIf = config.retryIf
    val log = logger
    val source = this

    // [config] is an immutable value already defaulted and validated at its own construction.
    // The cold-Flow analog of [ExponentialBackoffPolicyImpl.execute]: the [Backoff] is created inside
    // the [flow] builder, so each collection of the returned cold Flow — not the one-time operator
    // application — gets its own escalating-delay progression starting from `initialDelay`. Two
    // concurrent collectors therefore never share (or race on) backoff state.
    return flow {
        val backoff =
            Backoff(
                initialDelay = config.initialDelay,
                maxDelay = config.maxDelay,
                multiplier = config.multiplier,
                useJitter = config.useJitter,
            )

        emitAll(
            source.retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause

                val exhausted = attempt >= config.maxAttempts - 1
                if (isTerminal(cause, retryIf) || exhausted) {
                    false
                } else {
                    val nextDelay = backoff.next()
                    log
                        .withValue("attempt", attempt + 1)
                        .withValue("max_attempts", config.maxAttempts)
                        .withValue("delay", nextDelay)
                        .withError(cause)
                        .warn("retry: flow collection failed, retrying after delay")
                    delay(nextDelay)
                    true
                }
            },
        )
    }
}
