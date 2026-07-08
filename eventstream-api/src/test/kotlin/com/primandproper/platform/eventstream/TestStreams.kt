package com.primandproper.platform.eventstream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/** Records every event sent, for asserting fan-out. Analog of the Go `mockStream`. */
class MockStream : EventStream {
    private val _done = CompletableDeferred<Unit>()
    private val _events = mutableListOf<Event>()

    val events: List<Event> get() = synchronized(_events) { _events.toList() }

    override suspend fun send(event: Event) {
        synchronized(_events) { _events += event }
    }

    override val done: Job get() = _done

    override suspend fun close() {
        _done.complete(Unit)
    }
}

/** Always fails on send, exercising the manager's per-stream error handling. Analog of `failingStream`. */
class FailingStream : EventStream {
    private val _done = CompletableDeferred<Unit>()

    override suspend fun send(event: Event): Unit = throw RuntimeException("stub error")

    override val done: Job get() = _done

    override suspend fun close() {
        _done.complete(Unit)
    }
}

/** Parks in [send] until [release] is completed, modeling a stalled client. Analog of `blockingStream`. */
class BlockingStream : EventStream {
    val started: CompletableDeferred<Unit> = CompletableDeferred()
    val release: CompletableDeferred<Unit> = CompletableDeferred()
    private val _done = CompletableDeferred<Unit>()

    override suspend fun send(event: Event) {
        started.complete(Unit)
        release.await()
    }

    override val done: Job get() = _done

    override suspend fun close() {
        _done.complete(Unit)
    }
}
