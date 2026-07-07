package com.primandproper.platform.analytics.segment

import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ensureCircuitBreaker
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.segment.analytics.Analytics
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.MessageBuilder
import com.segment.analytics.messages.TrackMessage

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
 * enqueue under the injected [CircuitBreaker]. In platform-go the breaker is driven from the Segment
 * client's asynchronous delivery callbacks (`breakerCallback.Success/Failure`); the coroutine-native
 * Kotlin breaker is `execute`-shaped, so this port drives it from the synchronous enqueue outcome
 * instead — an open breaker rejects with `ErrCircuitBroken` before enqueuing, and an enqueue failure
 * is counted as a breaker failure. Delivery still happens asynchronously in the SDK's background
 * flusher.
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
    override fun close() {
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
 * Builds a production Segment-backed [EventReporter].
 *
 * @param apiToken the Segment write key; an empty token throws [EmptyApiTokenException] (mirroring Go).
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun SegmentEventReporter(
    apiToken: String,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    circuitBreaker: CircuitBreaker? = null,
): SegmentEventReporter {
    if (apiToken.isEmpty()) throw EmptyApiTokenException()

    val analytics = Analytics.builder(apiToken).build()

    return SegmentEventReporter(
        o11y = Observer(NAME, logger, tracerProvider),
        circuitBreaker = ensureCircuitBreaker(circuitBreaker),
        enqueuer = MessageEnqueuer { analytics.enqueue(it) },
        closer = {
            analytics.flush()
            analytics.shutdown()
        },
    )
}
