package com.primandproper.platform.eventstream.noop

import com.primandproper.platform.eventstream.BidirectionalEventStream
import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A no-op [EventStream]: [send] is discarded and [done] completes only on [close]. Port of
 * platform-go's `eventstream/noop.EventStream` — a safe default for wiring and for tests that don't
 * exercise a real transport.
 *
 * Go's `Done()` returns a `chan struct{}` closed once by a `sync.Once`; the coroutine analog is a
 * [CompletableDeferred] exposed as the [done] [Job], and [close] completes it idempotently.
 */
public open class NoopEventStream : EventStream {
    private val _done = CompletableDeferred<Unit>()

    override val done: Job get() = _done

    override suspend fun send(event: Event) {
    }

    override suspend fun close() {
        _done.complete(Unit)
    }
}

/**
 * A no-op [BidirectionalEventStream]. Port of platform-go's
 * `eventstream/noop.BidirectionalEventStream`.
 *
 * Go's `Receive()` returns a channel that never delivers an event; the faithful [Flow] analog is a
 * flow that emits nothing and suspends (via `awaitCancellation`) until the collector is cancelled,
 * rather than completing immediately — so a collector waits exactly as it would on the never-ready Go
 * channel.
 */
public class NoopBidirectionalEventStream :
    NoopEventStream(),
    BidirectionalEventStream {
    override fun receive(): Flow<Event> = flow { awaitCancellation() }
}
