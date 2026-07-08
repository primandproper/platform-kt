package com.primandproper.platform.httpclient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

/** The result of a single HTTP attempt handed to a [RetryHook]: either a response or a thrown error. */
public sealed interface HttpOutcome {
    public data class Received(val response: HttpResponse) : HttpOutcome

    public data class Failed(val error: Throwable) : HttpOutcome
}

/**
 * The retry seam. Given the [attempt] number (1-based), the [request], and the [outcome] it produced,
 * returns how long to wait before trying again — or null to stop and surface [outcome]. This is a
 * hook, not a policy: the actual exponential-backoff-with-jitter logic belongs to the `retry`
 * package, which will supply an implementation here.
 */
public fun interface RetryHook {
    public suspend fun nextDelay(
        attempt: Int,
        request: HttpRequest,
        outcome: HttpOutcome,
    ): Duration?
}

/**
 * Drives [attempt] through [HttpClientConfig.retryHook], centralizing the retry loop so both backends
 * share one seam instead of each rolling their own. With no hook configured it collapses to a single
 * call. A [CancellationException] is never swallowed — coroutine cancellation always wins over retry.
 */
public suspend fun HttpClientConfig.executeWithRetries(
    request: HttpRequest,
    attempt: suspend () -> HttpResponse,
): HttpResponse {
    val hook = retryHook ?: return attempt()

    var attemptNumber = 0
    while (true) {
        attemptNumber++
        val outcome =
            try {
                HttpOutcome.Received(attempt())
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                HttpOutcome.Failed(t)
            }

        val wait =
            hook.nextDelay(attemptNumber, request, outcome)
                ?: return when (outcome) {
                    is HttpOutcome.Received -> outcome.response
                    is HttpOutcome.Failed -> throw outcome.error
                }

        delay(wait)
    }
}
