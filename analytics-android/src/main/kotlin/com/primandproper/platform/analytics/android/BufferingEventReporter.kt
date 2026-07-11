package com.primandproper.platform.analytics.android

import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span

/** Component name for the buffering reporter's Observer. */
internal const val NAME: String = "buffering_event_reporter"

/** Default maximum number of events retained before the oldest are dropped. */
public const val DEFAULT_BUFFER_CAPACITY: Int = 1000

/** A single captured analytics event held by [BufferingEventReporter]. */
public sealed interface BufferedEvent {
    /** An `identify` — a user's traits. */
    public data class Identify(
        val userID: String,
        val properties: Map<String, Any?>,
    ) : BufferedEvent

    /** A `track` — a named event, for an identified or anonymous user. */
    public data class Track(
        val event: String,
        val userID: String,
        val anonymous: Boolean,
        val properties: Map<String, Any?>,
    ) : BufferedEvent
}

/**
 * An on-device [EventReporter] that buffers events in memory rather than delivering them to a vendor
 * — the local Android backend, and an offline / pre-write-key stand-in for the network-backed
 * [SegmentEventReporter] (both implement the same interface, so callers swap without changing code).
 *
 * The buffer is bounded ([capacity]) and thread-safe: when full, the oldest event is dropped so a
 * burst can never exhaust memory. Every public operation opens an Observer span recording the
 * user/event/length, matching how the other reporters instrument. Once [close]d, further events are
 * ignored.
 *
 * ## The buffer is in-memory and process-local — NOT durable
 * Events live only in this instance's heap. There is no disk persistence: if the process is killed
 * (backgrounded and reclaimed, crash, reboot) before a consumer [drain]s and forwards them, the
 * buffered events are lost. A consumer flushes the buffer with [drain] and forwards the events to the
 * real transport, or inspects it non-destructively with [snapshot]; scheduling that drain (e.g. from a
 * `WorkManager` job) does not by itself make the events survive process death.
 *
 * TODO(persist-buffer): back the buffer with durable storage so events outlive the process. Intended
 * shape: persist each captured [BufferedEvent] to Jetpack DataStore (or a Room table) on
 * [record] instead of (or alongside) the in-memory deque, and run a `WorkManager` `CoroutineWorker`
 * that reads the persisted rows, forwards them to the real transport, and deletes the drained rows on
 * success. Only then does the "flush from a WorkManager job" story actually survive process death.
 */
public class BufferingEventReporter internal constructor(
    private val o11y: Observer,
    private val capacity: Int,
) : EventReporter {
    /**
     * @param capacity maximum retained events before the oldest is dropped.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop.
     */
    public constructor(
        capacity: Int = DEFAULT_BUFFER_CAPACITY,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer(NAME, logger, tracerProvider), capacity)

    private val lock = Any()
    private val buffer = ArrayDeque<BufferedEvent>()
    private var closed = false

    override suspend fun close() {
        synchronized(lock) {
            buffer.clear()
            closed = true
        }
    }

    override suspend fun addUser(
        userID: String,
        properties: Map<String, Any?>,
    ): Unit =
        o11y.span("AddUser") {
            set(Keys.USER_ID, userID)
            set(Keys.LENGTH, properties.size)
            record(BufferedEvent.Identify(userID, properties.toMap()))
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
            record(BufferedEvent.Track(event, userID, anonymous, properties.toMap()))
        }

    private fun record(event: BufferedEvent) {
        synchronized(lock) {
            if (closed) return
            buffer.addLast(event)
            while (buffer.size > capacity) buffer.removeFirst()
        }
    }

    /** A non-destructive copy of the buffered events, oldest first. */
    public fun snapshot(): List<BufferedEvent> = synchronized(lock) { buffer.toList() }

    /** Returns and removes all buffered events, oldest first — the flush entry point. */
    public fun drain(): List<BufferedEvent> =
        synchronized(lock) {
            val out = buffer.toList()
            buffer.clear()
            out
        }

    /** Current number of buffered events. */
    public val size: Int get() = synchronized(lock) { buffer.size }
}
