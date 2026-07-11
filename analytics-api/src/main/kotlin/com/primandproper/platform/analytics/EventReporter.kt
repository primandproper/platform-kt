package com.primandproper.platform.analytics

import com.primandproper.platform.observability.SuspendCloseable

/**
 * Collects data about customers — the port of platform-go's `analytics.EventReporter`.
 *
 * The Go interface threads a `context.Context` through every method and returns an `error`; the
 * coroutine-native Kotlin analog drops the explicit context (trace context rides the coroutine
 * scope, installed by the Observer span) and signals failure by throwing rather than returning an
 * error value. Delivery-oriented methods are therefore `suspend`, so a backend can await its
 * circuit breaker / transport without blocking a thread.
 */
public interface EventReporter : SuspendCloseable {
    /**
     * Flushes buffered events and releases the underlying client. Safe to call more than once.
     * `suspend` via [SuspendCloseable] because the flush is delivery I/O.
     */
    override suspend fun close()

    /**
     * Upserts a user's identity, forwarding [properties] as user traits. Mirrors Go's `AddUser`
     * (a Segment `identify`).
     */
    public suspend fun addUser(
        userID: String,
        properties: Map<String, Any?> = emptyMap(),
    )

    /** Records [event] for the identified user [userID]. Mirrors Go's `EventOccurred` (a `track`). */
    public suspend fun eventOccurred(
        event: String,
        userID: String,
        properties: Map<String, Any?> = emptyMap(),
    )

    /**
     * Records [event] for an anonymous user keyed by [anonymousID]. Mirrors Go's
     * `EventOccurredAnonymous` (a `track` with an anonymous id).
     */
    public suspend fun eventOccurredAnonymous(
        event: String,
        anonymousID: String,
        properties: Map<String, Any?> = emptyMap(),
    )
}
