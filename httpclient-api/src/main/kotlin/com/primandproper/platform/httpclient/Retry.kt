package com.primandproper.platform.httpclient

import com.primandproper.platform.observability.Keys
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
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

    val span = Span.current()
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

        val wait = hook.nextDelay(attemptNumber, request, outcome)
        if (wait == null) {
            // Done retrying: publish how many retries the call cost (attempts past the first) so a
            // trace shows the total without having to count the per-attempt events below.
            span.recordRetryCount(attemptNumber - 1)
            return when (outcome) {
                is HttpOutcome.Received -> outcome.response
                is HttpOutcome.Failed -> throw outcome.error
            }
        }

        // About to retry: leave a breadcrumb for the attempt that just failed. Otherwise the retried
        // errors are swallowed — only the final outcome would ever reach the span.
        span.recordRetryAttempt(attemptNumber, outcome)
        delay(wait)
    }
}

/**
 * Adds a span event for a single failed-and-retried [attempt], carrying the attempt number and the
 * error (or the response status, when a non-exceptional response is what triggered the retry). No-ops
 * on a non-recording span (noop tracer / sampled-out), mirroring [Span.recordHttpRequest].
 */
private fun Span.recordRetryAttempt(
    attempt: Int,
    outcome: HttpOutcome,
) {
    if (!isRecording) return
    val attributes =
        Attributes.builder()
            .put("http.retry_attempt", attempt.toLong())
            .apply {
                when (outcome) {
                    is HttpOutcome.Failed -> {
                        put("exception.type", outcome.error.javaClass.name)
                        outcome.error.message?.let { put("exception.message", it) }
                    }
                    is HttpOutcome.Received -> put(Keys.RESPONSE_STATUS, outcome.response.statusCode.toLong())
                }
            }
            .build()
    addEvent("http.retry", attributes)
}

/** Records the total number of retries on the span as `http.retry_count`. No-ops when not recording. */
private fun Span.recordRetryCount(retries: Int) {
    if (!isRecording) return
    setAttribute("http.retry_count", retries.toLong())
}
