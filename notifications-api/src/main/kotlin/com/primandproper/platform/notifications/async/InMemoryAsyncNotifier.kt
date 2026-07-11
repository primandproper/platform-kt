package com.primandproper.platform.notifications.async

import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration

/** Component name for the in-memory notifier's Observer. */
internal const val NAME: String = "in_memory_async_notifier"

/** Default number of delivery attempts per subscriber before a publish fails. */
public const val DEFAULT_MAX_ATTEMPTS: Int = 3

/** A live subscription to a channel. Cancel it to stop receiving events. */
public fun interface Subscription {
    /** Removes this subscriber; subsequent publishes skip it. Idempotent. */
    public fun cancel()
}

/**
 * An in-process [AsyncNotifier] with deterministic, retrying delivery — the local backend that stands
 * in for platform-go's networked async backends (WebSocket/SSE/Pusher/Ably), which need a live
 * transport and are documented seams here.
 *
 * DELIVERY ORDERING: [publish] delivers an event to a channel's subscribers **synchronously, in
 * registration order**, and suspends until every subscriber has been handled. Because a single
 * publisher's calls run to completion one after another, the events a given subscriber sees preserve
 * the publish order — the ordering guarantee the WebSocket/SSE backends provide over a single
 * connection.
 *
 * RETRY: a subscriber handler that throws is retried up to [maxAttempts] times (with an optional
 * [retryDelay] between attempts). A transient failure that later succeeds is delivered exactly once
 * overall; if a subscriber exhausts its attempts the failure is recorded on the span and rethrown, so
 * [publish] fails fast rather than silently dropping. A [CancellationException] propagates immediately
 * and is never retried, so a cancelled coroutine tears down cleanly.
 *
 * Every [publish] opens an Observer span recording the channel, event type, and subscriber count,
 * matching the instrumentation convention of the other backends.
 */
public class InMemoryAsyncNotifier internal constructor(
    private val o11y: Observer,
    private val maxAttempts: Int,
    private val retryDelay: Duration,
) : AsyncNotifier {
    /**
     * @param maxAttempts delivery attempts per subscriber before a publish fails (must be >= 1).
     * @param retryDelay optional pause between attempts; [Duration.ZERO] retries immediately.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop.
     */
    public constructor(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelay: Duration = Duration.ZERO,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer(NAME, logger, tracerProvider), maxAttempts, retryDelay) {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    }

    private class Handler(
        val channel: String,
        val block: suspend (AsyncEvent) -> Unit,
    )

    private val lock = Any()
    private val handlers = mutableListOf<Handler>()
    private var closed = false

    /**
     * Registers [handler] for [channel] and returns a [Subscription]. Handlers are delivered to in
     * the order they subscribed. Subscribing after [close] returns an already-cancelled subscription.
     */
    public fun subscribe(
        channel: String,
        handler: suspend (AsyncEvent) -> Unit,
    ): Subscription {
        val entry = Handler(channel, handler)
        synchronized(lock) {
            if (!closed) handlers += entry
        }
        return Subscription {
            synchronized(lock) { handlers.remove(entry) }
        }
    }

    /**
     * A `Flow`-based alternative to [subscribe]: a **cold** flow of every [AsyncEvent] published to
     * [channel]. Collecting subscribes; cancelling or completing the collector unsubscribes (Go's
     * subscription teardown), so the caller never has to hold and [Subscription.cancel] a handle. The
     * flow is a `callbackFlow` over the same [subscribe]/[Subscription.cancel] machinery, so delivery
     * ordering and channel filtering match [publish] exactly. Backpressure propagates: a slow collector
     * suspends the publishing [publish] call, matching a single-connection transport.
     */
    public fun events(channel: String): Flow<AsyncEvent> =
        callbackFlow {
            val subscription = subscribe(channel) { event -> send(event) }
            awaitClose { subscription.cancel() }
        }

    override suspend fun publish(
        channel: String,
        event: AsyncEvent,
    ): Unit =
        o11y.span("Publish") {
            set(NotificationKeys.CHANNEL, channel)
            set(NotificationKeys.EVENT_TYPE, event.type)
            val targets = synchronized(lock) { handlers.filter { it.channel == channel } }
            set(Keys.LENGTH, targets.size)
            for (target in targets) {
                deliverWithRetry(target, event)
            }
        }

    private suspend fun deliverWithRetry(
        target: Handler,
        event: AsyncEvent,
    ) {
        var attempt = 0
        while (true) {
            attempt++
            try {
                target.block(event)
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (attempt >= maxAttempts) throw error
                // Intermediate failures are recoverable but were previously silent; surface them at warn
                // so a flapping subscriber is visible even when a later attempt ultimately succeeds.
                o11y.logger
                    .withError(error)
                    .warn("async delivery to channel '${target.channel}' failed on attempt $attempt; retrying")
                if (retryDelay > Duration.ZERO) delay(retryDelay)
            }
        }
    }

    override suspend fun close() {
        synchronized(lock) {
            handlers.clear()
            closed = true
        }
    }
}
