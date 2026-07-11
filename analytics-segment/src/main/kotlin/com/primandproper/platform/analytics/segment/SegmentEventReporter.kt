package com.primandproper.platform.analytics.segment

import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.segment.analytics.Analytics
import com.segment.analytics.Callback
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.Message
import com.segment.analytics.messages.MessageBuilder
import com.segment.analytics.messages.TrackMessage
import kotlinx.coroutines.runBlocking

/** Component name for the Segment reporter's Observer. Mirrors platform-go's `segment.name`. */
internal const val NAME: String = "segment_event_reporter"

/**
 * Thrown when an empty Segment API token is supplied. Port of platform-go's `segment.ErrEmptyAPIToken`.
 */
public class EmptyApiTokenException : IllegalArgumentException("empty Segment API token")

/**
 * The seam through which the reporter hands a built message to Segment. Production wires it to
 * `Analytics.enqueue`; tests supply a capturing implementation so the message-building/adaptation
 * logic is exercised without any network delivery.
 */
internal fun interface MessageEnqueuer {
    fun enqueue(builder: MessageBuilder<*, *>)
}

/**
 * A Segment-backed [EventReporter] — the port of platform-go's `segment.EventReporter`.
 *
 * Each method opens an Observer span, records the user/event/length on both pillars, then runs the
 * enqueue under the injected [CircuitBreaker]. An open breaker rejects with `ErrCircuitBroken` before
 * enqueuing, and a synchronous enqueue failure is counted as a breaker failure. Delivery itself
 * happens asynchronously in the SDK's background flusher; that outcome is reported back through a
 * [SegmentDeliveryCallback] (registered by the production factory below), mirroring how platform-go
 * drives its breaker from the Segment client's delivery callbacks (`breakerCallback.Success/Failure`)
 * — so a batch that fails to deliver logs an error and counts toward the breaker instead of failing
 * forever in silence.
 *
 * TODO(metrics): platform-go increments explicit event/error `Int64Counter`s per call. Those are a
 * metrics-pillar seam here (the span already records the operation); wire counters once the
 * observability metrics surface lands, matching how `:circuitbreaking` left `TODO(metrics)`.
 */
public class SegmentEventReporter internal constructor(
    private val o11y: Observer,
    private val circuitBreaker: CircuitBreaker,
    private val enqueuer: MessageEnqueuer,
    private val closer: () -> Unit,
) : EventReporter {
    override suspend fun close() {
        try {
            closer()
        } catch (error: Throwable) {
            o11y.logger.error("closing segment client", error)
        }
    }

    override suspend fun addUser(
        userID: String,
        properties: Map<String, Any?>,
    ): Unit =
        o11y.span("AddUser") {
            set(Keys.USER_ID, userID)
            set(Keys.LENGTH, properties.size)
            circuitBreaker.execute {
                enqueuer.enqueue(IdentifyMessage.builder().userId(userID).traits(properties))
            }
        }

    override suspend fun eventOccurred(
        event: String,
        userID: String,
        properties: Map<String, Any?>,
    ): Unit = track(event, userID, anonymous = false, properties = properties)

    override suspend fun eventOccurredAnonymous(
        event: String,
        anonymousID: String,
        properties: Map<String, Any?>,
    ): Unit = track(event, anonymousID, anonymous = true, properties = properties)

    private suspend fun track(
        event: String,
        userID: String,
        anonymous: Boolean,
        properties: Map<String, Any?>,
    ): Unit =
        o11y.span("EventOccurred") {
            set("event", event)
            set(Keys.USER_ID, userID)
            set(Keys.LENGTH, properties.size)
            set("anonymous", anonymous)
            circuitBreaker.execute {
                val builder = TrackMessage.builder(event).properties(properties)
                if (anonymous) builder.anonymousId(userID) else builder.userId(userID)
                enqueuer.enqueue(builder)
            }
        }
}

/**
 * Reports the Segment SDK's asynchronous delivery outcome back onto the breaker and logger. The
 * synchronous enqueue in [SegmentEventReporter] only sees a message land in the SDK's in-memory queue;
 * actual HTTP delivery happens later on the SDK's background flusher, and *that* is the outcome this
 * callback reports — a delivered batch counts a breaker success, a failed one logs an error and counts
 * a breaker failure, so repeated delivery failures trip the breaker rather than failing silently. Port
 * of platform-go's `breakerCallback`.
 *
 * The callback fires on the SDK's network thread, off the coroutine world; the breaker's only surface
 * is the suspend [CircuitBreaker.execute], so the outcome is bridged with [runBlocking] on that thread
 * — a brief block on a background flusher thread, never on a caller's coroutine. Only the throwable is
 * logged (never the message body), so no user traits or credentials reach the log.
 */
internal class SegmentDeliveryCallback(
    private val logger: Logger,
    private val circuitBreaker: CircuitBreaker,
) : Callback {
    override fun success(message: Message): Unit = report(null)

    override fun failure(
        message: Message,
        throwable: Throwable,
    ) {
        logger.error("segment message delivery failed", throwable)
        report(throwable)
    }

    private fun report(failure: Throwable?) {
        try {
            runBlocking { circuitBreaker.execute { failure?.let { throw it } } }
        } catch (_: Throwable) {
            // execute rethrows the delivery failure (or a rejection when the breaker is already open);
            // the outcome is already recorded and there is nothing to propagate from a background thread.
        }
    }
}

/**
 * Builds a production Segment-backed [EventReporter].
 *
 * @param apiToken the Segment write key; an empty token throws [EmptyApiTokenException] (mirroring Go).
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun SegmentEventReporter(
    apiToken: String,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): SegmentEventReporter {
    if (apiToken.isEmpty()) throw EmptyApiTokenException()

    val o11y = Observer(NAME, logger, tracerProvider)
    val breaker = circuitBreaker

    // Register the delivery callback so the SDK's background flush drives the breaker and logs
    // failures, instead of the breaker seeing only the synchronous enqueue.
    val analytics =
        Analytics.builder(apiToken)
            .callback(SegmentDeliveryCallback(o11y.logger, breaker))
            .build()

    return SegmentEventReporter(
        o11y = o11y,
        circuitBreaker = breaker,
        enqueuer = MessageEnqueuer { analytics.enqueue(it) },
        closer = {
            analytics.flush()
            analytics.shutdown()
        },
    )
}
