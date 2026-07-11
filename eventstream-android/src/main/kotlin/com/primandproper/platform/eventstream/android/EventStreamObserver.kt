package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event

/**
 * Optional observability seam for the consume-side event streams ([SseEventSource],
 * [WebSocketEventClient]). Without one, a frame that fails to decode or an event that can't be handed
 * to a slow collector is dropped *silently* — the review flagged that >64-event bursts and every
 * malformed frame vanish with no signal. Implementations receive a callback (with a running drop
 * count) so a caller can log or meter the loss; the default [NoopEventStreamObserver] does nothing,
 * preserving the zero-dependency behavior.
 */
public interface EventStreamObserver {
    /**
     * An inbound WebSocket frame could not be decoded into an [Event] and was skipped (the read loop's
     * "skip malformed and continue"). [droppedTotal] is the running number of frames this stream has
     * dropped so far.
     */
    public fun onUndecodableFrame(
        raw: String,
        droppedTotal: Long,
    )

    /**
     * A decoded [event] could not be delivered because the flow's buffer was full or already closed
     * (backpressure). [droppedTotal] is the running number of frames this stream has dropped so far.
     */
    public fun onDroppedEvent(
        event: Event,
        droppedTotal: Long,
    )
}

/** The default [EventStreamObserver]: silently ignores every drop, matching the pre-observability behavior. */
public object NoopEventStreamObserver : EventStreamObserver {
    override fun onUndecodableFrame(
        raw: String,
        droppedTotal: Long,
    ) {}

    override fun onDroppedEvent(
        event: Event,
        droppedTotal: Long,
    ) {}
}
