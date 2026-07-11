package com.primandproper.platform.analytics.android

import android.content.Context
import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Configuration
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import com.segment.analytics.kotlin.core.Analytics as SegmentAnalytics

/** Component name for the Segment Android reporter's Observer. */
internal const val SEGMENT_NAME: String = "segment_android_event_reporter"

/** Thrown when an empty Segment write key is supplied. Mirrors the server reporter's empty-token guard. */
public class EmptyWriteKeyException : IllegalArgumentException("empty Segment write key")

/**
 * The seam through which the reporter drives the Segment SDK. Production wires it to a live
 * `Analytics` (see [liveSegmentClient]); tests supply a capturing fake so the identity/property
 * mapping and observability are exercised off-device without any Context or network delivery — the
 * on-device analog of the server reporter's `MessageEnqueuer` seam.
 */
internal interface SegmentClient {
    /** Upserts the user's identity and traits (a Segment `identify`). */
    fun identify(
        userId: String,
        traits: JsonObject,
    )

    /**
     * Records a `track`. Exactly one of [userId]/[anonymousId] is non-null and is stamped on the
     * event so a call's subject is honored without disturbing the SDK's ambient identity.
     */
    fun track(
        event: String,
        properties: JsonObject,
        userId: String?,
        anonymousId: String?,
    )
}

/**
 * A Segment-backed on-device [EventReporter] over the Segment Analytics-Kotlin (Android) SDK — the
 * production delivery backend that replaces the in-memory [BufferingEventReporter] once a write key
 * and Context are available. Each method opens an Observer span recording the user/event/length
 * (matching the other reporters), then hands the call to the Segment SDK, which batches and delivers
 * on its own background flusher.
 *
 * Build it with the [SegmentEventReporter] factory (Context + write key). The internal seam
 * constructor is for tests.
 */
public class SegmentEventReporter internal constructor(
    private val o11y: Observer,
    private val client: SegmentClient,
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
            client.identify(userID, properties.toJsonObject())
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
            client.track(
                event = event,
                properties = properties.toJsonObject(),
                userId = if (anonymous) null else userID,
                anonymousId = if (anonymous) userID else null,
            )
        }
}

/** The production [SegmentClient]: a thin adapter over a live [SegmentAnalytics]. */
private class LiveSegmentClient(private val analytics: SegmentAnalytics) : SegmentClient {
    override fun identify(
        userId: String,
        traits: JsonObject,
    ) {
        analytics.identify(userId, traits)
    }

    override fun track(
        event: String,
        properties: JsonObject,
        userId: String?,
        anonymousId: String?,
    ) {
        // Stamp the call's subject on the event via the enrichment closure, so an explicit user id or
        // a specific anonymous id is honored per call without mutating the SDK's ambient identity.
        analytics.track(event, properties) { baseEvent ->
            baseEvent?.apply {
                if (userId != null) this.userId = userId
                if (anonymousId != null) this.anonymousId = anonymousId
            }
        }
    }
}

/**
 * Builds a production Segment-backed [EventReporter] over the Android SDK.
 *
 * @param context any Context (the Application context is used); the SDK requires it for on-device
 *   storage and lifecycle tracking.
 * @param writeKey the Segment source write key; empty throws [EmptyWriteKeyException].
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param configure applied to the SDK [Configuration] before the client is built (flush cadence,
 *   lifecycle tracking, …); the Android factory sets the application for you.
 */
public fun SegmentEventReporter(
    context: Context,
    writeKey: String,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    configure: Configuration.() -> Unit = {},
): SegmentEventReporter {
    if (writeKey.isEmpty()) throw EmptyWriteKeyException()

    val o11y = Observer(SEGMENT_NAME, logger, tracerProvider)
    val analytics = Analytics(writeKey, context.applicationContext, configure)

    return SegmentEventReporter(
        o11y = o11y,
        client = LiveSegmentClient(analytics),
        closer = { analytics.flush() },
    )
}

/** Converts caller-supplied analytics properties into the [JsonObject] the Segment SDK carries. */
internal fun Map<String, Any?>.toJsonObject(): JsonObject =
    buildJsonObject {
        for ((key, value) in this@toJsonObject) put(key, value.toJsonElement())
    }

/** Widens an arbitrary property value into a [JsonElement]; unknown types fall back to their string form. */
private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> ->
            buildJsonObject {
                for ((k, v) in this@toJsonElement) put(k.toString(), v.toJsonElement())
            }
        is Iterable<*> ->
            buildJsonArray {
                for (item in this@toJsonElement) add(item.toJsonElement())
            }
        else -> JsonPrimitive(this.toString())
    }
