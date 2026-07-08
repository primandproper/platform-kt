package com.primandproper.platform.notifications.android

import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span

/** Component name for the dispatcher's Observer. */
internal const val NAME: String = "push_message_dispatcher"

/**
 * Bridges a delivered push to the app's [PushMessageHandler] — the receive-side entry point that a
 * concrete `FirebaseMessagingService` (the `TODO(fcm-android)` seam) calls into.
 *
 * On each delivery it opens an Observer span, maps the [RawPushPayload] via [PushPayloadMapper], and —
 * when the payload is usable — records the title/message-id (never a token; see [NotificationKeys]'s
 * redaction note) and invokes [PushMessageHandler.onMessageReceived]. A payload that maps to `null`
 * (see [PushPayloadMapper]'s malformed rule) is dropped: [dispatch] returns `false` and the handler is
 * not called, so a junk push can never crash the app or reach handler code with a fabricated message.
 *
 * Kept off any Firebase type so the whole dispatch flow is unit-testable on the JVM; the service seam
 * is the only piece that touches `RemoteMessage`.
 */
public class PushMessageDispatcher internal constructor(
    private val o11y: Observer,
    private val handler: PushMessageHandler,
) {
    /**
     * @param handler the app's push handler.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop.
     */
    public constructor(
        handler: PushMessageHandler,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(Observer(NAME, logger, tracerProvider), handler)

    /**
     * Maps and dispatches [payload]. Returns `true` when it was mapped and handed to the handler,
     * `false` when it was malformed and dropped.
     */
    public suspend fun dispatch(payload: RawPushPayload): Boolean =
        o11y.span("OnMessageReceived") {
            val message = PushPayloadMapper.map(payload)
            if (message == null) {
                logger.debug("dropping unmappable push payload")
                return@span false
            }
            set(NotificationKeys.TITLE, message.title)
            if (message.messageId.isNotEmpty()) set(NotificationKeys.MESSAGE_ID, message.messageId)
            handler.onMessageReceived(message)
            true
        }

    /** Forwards a rotated registration [token] to the handler, in a span. */
    public suspend fun dispatchToken(token: String): Unit =
        o11y.span("OnNewToken") {
            handler.onNewToken(token)
        }
}
