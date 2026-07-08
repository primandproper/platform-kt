package com.primandproper.platform.notifications.async.noop

import com.primandproper.platform.notifications.async.AsyncEvent
import com.primandproper.platform.notifications.async.AsyncNotifier

/**
 * An [AsyncNotifier] that discards every event — the port of platform-go's `async/noop.asyncNotifier`.
 * The safe default when no async backend is configured, and for tests that don't care about delivery.
 */
public class NoopAsyncNotifier : AsyncNotifier {
    override suspend fun publish(
        channel: String,
        event: AsyncEvent,
    ) {
    }

    override fun close() {
    }
}
