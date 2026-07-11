package com.primandproper.platform.analytics.noop

import com.primandproper.platform.analytics.EventReporter

/**
 * A no-op [EventReporter]: every method returns immediately and reports nothing. Port of
 * platform-go's `analytics/noop` — the safe default when a source has no provider configured, and a
 * stand-in for tests that don't care about delivery.
 */
public object NoopEventReporter : EventReporter {
    override suspend fun close() {}

    override suspend fun addUser(
        userID: String,
        properties: Map<String, Any?>,
    ) {}

    override suspend fun eventOccurred(
        event: String,
        userID: String,
        properties: Map<String, Any?>,
    ) {}

    override suspend fun eventOccurredAnonymous(
        event: String,
        anonymousID: String,
        properties: Map<String, Any?>,
    ) {}
}
