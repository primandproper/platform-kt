package com.primandproper.platform.analytics.multisource

import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.analytics.noop.NoopEventReporter
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span

/** Component name for the multisource reporter's Observer. */
internal const val NAME: String = "multisource_event_reporter"

/**
 * The event property used to identify the analytics source (e.g. `ios`, `web`). For PostHog, where a
 * single API key is shared across sources, this property distinguishes events. Port of
 * platform-go's `multisource.SourcePropertyKey`.
 */
public const val SOURCE_PROPERTY_KEY: String = "source"

/**
 * Delegates events to per-source [EventReporter]s — the port of platform-go's
 * `multisource.MultiSourceEventReporter`. The reporters map is populated at construction and never
 * mutated afterwards, so reads need no synchronization.
 *
 * Wiring the map from config (platform-go's `ProvideMultiSourceEventReporter`, which instantiates a
 * Segment/PostHog client per source and dedupes PostHog clients by API key) belongs to a backend
 * wiring layer that can see the concrete backends — left as a `TODO(wiring)` seam here, since this
 * `:analytics-api` module deliberately does not depend on any vendor backend.
 */
public class MultiSourceEventReporter internal constructor(
    reporters: Map<String, EventReporter>,
    private val o11y: Observer,
) {
    internal val reporters: Map<String, EventReporter> = reporters

    /**
     * @param reporters per-source reporters; defaults to an empty map.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop.
     */
    public constructor(
        reporters: Map<String, EventReporter> = emptyMap(),
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(reporters, Observer(NAME, logger, tracerProvider))

    /** Returns the reporter for [source], or [NoopEventReporter] if unknown/missing. */
    internal fun getReporter(source: String): EventReporter {
        val r = reporters[source]
        if (r != null) return r
        o11y.logger
            .withValue(SOURCE_PROPERTY_KEY, source)
            .withValue("known_sources", reporters.keys.toList())
            .info("no analytics reporter configured for source, using noop")
        return NoopEventReporter
    }

    /**
     * Flushes and closes every underlying reporter. A reporter shared across multiple sources
     * (e.g. PostHog sources with the same API key) is closed exactly once.
     */
    public suspend fun close() {
        val seen = mutableSetOf<EventReporter>()
        for (r in reporters.values) {
            if (seen.add(r)) r.close()
        }
    }

    /** Records an event for an identified user against [source]'s reporter. */
    public suspend fun trackEvent(
        source: String,
        event: String,
        userID: String,
        properties: Map<String, Any?> = emptyMap(),
    ): Unit =
        o11y.span("TrackEvent") {
            set(SOURCE_PROPERTY_KEY, source)
            set("event", event)
            set("user_id", userID)
            set(Keys.LENGTH, properties.size)
            getReporter(source).eventOccurred(event, userID, withSourceProperty(source, properties))
        }

    /** Identifies a user against [source]'s reporter, forwarding the user's traits. */
    public suspend fun addUser(
        source: String,
        userID: String,
        properties: Map<String, Any?> = emptyMap(),
    ): Unit =
        o11y.span("AddUser") {
            set(SOURCE_PROPERTY_KEY, source)
            set("user_id", userID)
            set(Keys.LENGTH, properties.size)
            getReporter(source).addUser(userID, withSourceProperty(source, properties))
        }

    /** Records an event for an anonymous user against [source]'s reporter. */
    public suspend fun trackAnonymousEvent(
        source: String,
        event: String,
        anonymousID: String,
        properties: Map<String, Any?> = emptyMap(),
    ): Unit =
        o11y.span("TrackAnonymousEvent") {
            set(SOURCE_PROPERTY_KEY, source)
            set("event", event)
            set("anonymous_id", anonymousID)
            set(Keys.LENGTH, properties.size)
            getReporter(source).eventOccurredAnonymous(event, anonymousID, withSourceProperty(source, properties))
        }
}

/**
 * Returns a copy of [properties] with the source property set. For PostHog (single API key across
 * sources), the source property distinguishes events. Never mutates the input. Mirrors
 * `multisource.withSourceProperty`.
 */
internal fun withSourceProperty(
    source: String,
    properties: Map<String, Any?>,
): Map<String, Any?> {
    val merged = LinkedHashMap<String, Any?>(properties)
    merged[SOURCE_PROPERTY_KEY] = source
    return merged
}
