package com.primandproper.platform.analytics.mock

import com.primandproper.platform.analytics.EventReporter

/**
 * A configurable [EventReporter] test double, mirroring platform-go's moq-generated
 * `analyticsmock.EventReporterMock`. Each method delegates to a settable `...Func`; calling a method
 * whose `Func` was left `null` throws [IllegalStateException] — the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's arguments are recorded in the
 * matching `...Calls` list, standing in for moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = EventReporterMock(eventOccurredFunc = { event, userID, props -> /* assert */ })
 * ```
 */
public class EventReporterMock(
    public var closeFunc: (() -> Unit)? = null,
    public var addUserFunc: (suspend (userID: String, properties: Map<String, Any?>) -> Unit)? = null,
    public var eventOccurredFunc: (suspend (event: String, userID: String, properties: Map<String, Any?>) -> Unit)? = null,
    public var eventOccurredAnonymousFunc: (suspend (event: String, anonymousID: String, properties: Map<String, Any?>) -> Unit)? = null,
) : EventReporter {
    /** Recorded call to [addUser]. */
    public data class AddUserCall(val userID: String, val properties: Map<String, Any?>)

    /** Recorded call to [eventOccurred]. */
    public data class EventOccurredCall(val event: String, val userID: String, val properties: Map<String, Any?>)

    /** Recorded call to [eventOccurredAnonymous]. */
    public data class EventOccurredAnonymousCall(val event: String, val anonymousID: String, val properties: Map<String, Any?>)

    private val lock = Any()

    private var _closeCalls = 0
    private val _addUserCalls = mutableListOf<AddUserCall>()
    private val _eventOccurredCalls = mutableListOf<EventOccurredCall>()
    private val _eventOccurredAnonymousCalls = mutableListOf<EventOccurredAnonymousCall>()

    /** Number of times [close] was called. */
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }
    public val addUserCalls: List<AddUserCall> get() = synchronized(lock) { _addUserCalls.toList() }
    public val eventOccurredCalls: List<EventOccurredCall> get() = synchronized(lock) { _eventOccurredCalls.toList() }
    public val eventOccurredAnonymousCalls: List<EventOccurredAnonymousCall>
        get() = synchronized(lock) { _eventOccurredAnonymousCalls.toList() }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        (closeFunc ?: error("EventReporterMock.closeFunc: method is null but was just called")).invoke()
    }

    override suspend fun addUser(
        userID: String,
        properties: Map<String, Any?>,
    ) {
        synchronized(lock) { _addUserCalls += AddUserCall(userID, properties) }
        (addUserFunc ?: error("EventReporterMock.addUserFunc: method is null but was just called")).invoke(userID, properties)
    }

    override suspend fun eventOccurred(
        event: String,
        userID: String,
        properties: Map<String, Any?>,
    ) {
        synchronized(lock) { _eventOccurredCalls += EventOccurredCall(event, userID, properties) }
        (eventOccurredFunc ?: error("EventReporterMock.eventOccurredFunc: method is null but was just called")).invoke(
            event,
            userID,
            properties,
        )
    }

    override suspend fun eventOccurredAnonymous(
        event: String,
        anonymousID: String,
        properties: Map<String, Any?>,
    ) {
        synchronized(lock) { _eventOccurredAnonymousCalls += EventOccurredAnonymousCall(event, anonymousID, properties) }
        (
            eventOccurredAnonymousFunc
                ?: error("EventReporterMock.eventOccurredAnonymousFunc: method is null but was just called")
        ).invoke(event, anonymousID, properties)
    }
}
