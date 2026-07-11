package com.primandproper.platform.eventstream

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.CancellationException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages active [EventStream]s grouped by group ID and member ID. Port of platform-go's
 * `eventstream.StreamManager[S]`.
 *
 * Every method opens an [Observer] span and records the same fields Go does — `group_id`,
 * `member_id`, `event.type`, and [Keys.LENGTH] (`keys.LengthKey`) — mirroring `m.o11y.Begin(ctx)` /
 * `op.Set(...)`. The type parameter [S] is bound to [EventStream] so a caller can register a concrete
 * transport stream and get it back from [get] without a cast, exactly as Go's generic `[S EventStream]`.
 *
 * Concurrency mirrors the Go original faithfully. The registry map is guarded by a
 * [ReentrantReadWriteLock] (the analog of Go's `sync.RWMutex`), and the broadcast/send paths
 * **snapshot the group under the read lock and release it before sending**: a stalled client must
 * never hold the manager lock and block [add]/[remove] — the exact invariant Go's comments call out
 * and its `doesNotWedgeOnSlowClient` test guards.
 *
 * [broadcastToGroup] / [broadcastToGroupFiltered] are intentionally fire-and-forget: a single
 * stream's [EventStream.send] failure is recorded on the operation (via `acknowledge`, Go's
 * `op.Acknowledge`) but does not halt the fan-out. [sendToMember] instead propagates its failure to
 * the caller, matching Go returning the `Send` error.
 */
public class StreamManager<S : EventStream> internal constructor(
    private val o11y: Observer,
) {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching Go's
     *   `NewStreamManager(nil, nil)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer(NAME, logger, tracerProvider))

    private val lock = ReentrantReadWriteLock()

    // group ID -> (member ID -> stream). Guarded by [lock], exactly like Go's `streams` map under its
    // RWMutex; nested plain maps (not ConcurrentHashMap) so the "remove empties the group" step stays
    // atomic with the removal, as in Go.
    private val streams: MutableMap<String, MutableMap<String, S>> = mutableMapOf()

    /** Registers [stream] for [groupId]/[memberId]. Port of Go's `Add`. */
    public suspend fun add(
        groupId: String,
        memberId: String,
        stream: S,
    ): Unit =
        o11y.span("Add") {
            set("group_id", groupId)
            set("member_id", memberId)
            lock.write { streams.getOrPut(groupId) { mutableMapOf() }[memberId] = stream }
        }

    /** Removes a stream, dropping the group when it becomes empty. Port of Go's `Remove`. */
    public suspend fun remove(
        groupId: String,
        memberId: String,
    ): Unit =
        o11y.span("Remove") {
            set("group_id", groupId)
            set("member_id", memberId)
            lock.write {
                val group = streams[groupId]
                if (group != null) {
                    group.remove(memberId)
                    if (group.isEmpty()) streams.remove(groupId)
                }
            }
        }

    /** Returns a specific stream, or `null` if not found (Go's zero value). Port of Go's `Get`. */
    public suspend fun get(
        groupId: String,
        memberId: String,
    ): S? =
        o11y.span("Get") {
            set("group_id", groupId)
            set("member_id", memberId)
            lock.read { streams[groupId]?.get(memberId) }
        }

    /** Returns all streams for [groupId]. Port of Go's `GetGroupStreams`. */
    public suspend fun getGroupStreams(groupId: String): List<S> =
        o11y.span("GetGroupStreams") {
            val out = lock.read { streams[groupId]?.values?.toList() ?: emptyList() }
            set("group_id", groupId)
            set(Keys.LENGTH, out.size)
            out
        }

    /** Sends [event] to every stream in [groupId], fire-and-forget. Port of Go's `BroadcastToGroup`. */
    public suspend fun broadcastToGroup(
        groupId: String,
        event: Event,
    ): Unit =
        o11y.span("BroadcastToGroup") {
            set("group_id", groupId)
            set("event.type", event.type)

            // Snapshot under the lock, then release it before sending: a stalled client must never
            // hold the manager lock and block add/remove.
            val snapshot = lock.read { streams[groupId]?.values?.toList() }
            if (snapshot != null) {
                set(Keys.LENGTH, snapshot.size)
                for (s in snapshot) {
                    try {
                        s.send(event)
                    } catch (t: CancellationException) {
                        // Cancellation must propagate, not be swallowed and keep fanning out.
                        throw t
                    } catch (t: Throwable) {
                        acknowledge(t, "sending event to stream")
                    }
                }
            }
        }

    /**
     * Sends [event] to the streams in [groupId] for which [includeFunc] returns true, fire-and-forget.
     * Port of Go's `BroadcastToGroupFiltered`.
     */
    public suspend fun broadcastToGroupFiltered(
        groupId: String,
        event: Event,
        includeFunc: (memberId: String) -> Boolean,
    ): Unit =
        o11y.span("BroadcastToGroupFiltered") {
            set("group_id", groupId)
            set("event.type", event.type)

            // Snapshot the group's (memberID, stream) pairs under the lock, then release it before sending.
            val snapshot = lock.read { streams[groupId]?.entries?.map { it.key to it.value } ?: emptyList() }
            for ((memberId, s) in snapshot) {
                if (includeFunc(memberId)) {
                    try {
                        s.send(event)
                    } catch (t: CancellationException) {
                        // Cancellation must propagate, not be swallowed and keep fanning out.
                        throw t
                    } catch (t: Throwable) {
                        acknowledge(t, "sending event to stream")
                    }
                }
            }
        }

    /**
     * Sends [event] to a specific member in [groupId], propagating any send failure to the caller.
     * A no-op when the member is absent. Port of Go's `SendToMember`.
     */
    public suspend fun sendToMember(
        groupId: String,
        memberId: String,
        event: Event,
    ): Unit =
        o11y.span("SendToMember") {
            set("group_id", groupId)
            set("member_id", memberId)
            set("event.type", event.type)

            val s = lock.read { streams[groupId]?.get(memberId) }
            if (s != null) s.send(event)
        }

    /** Whether [groupId] has any active streams. Port of Go's `GroupHasStreams`. */
    public suspend fun groupHasStreams(groupId: String): Boolean =
        o11y.span("GroupHasStreams") {
            set("group_id", groupId)
            lock.read { (streams[groupId]?.size ?: 0) > 0 }
        }

    /** The number of streams registered for [groupId]. Port of Go's `GetStreamCount`. */
    public suspend fun getStreamCount(groupId: String): Int =
        o11y.span("GetStreamCount") {
            val count = lock.read { streams[groupId]?.size ?: 0 }
            set("group_id", groupId)
            set(Keys.LENGTH, count)
            count
        }

    private companion object {
        const val NAME = "event_stream_manager"
    }
}
