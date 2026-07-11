package com.primandproper.platform.notifications.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * The concrete [FirebaseMessagingService] the OS instantiates when a push is delivered — the
 * `TODO(fcm-android)` seam, now wired. It is the ONLY class in this module that touches a
 * `com.google.firebase` type: it copies the delivered [RemoteMessage] into the Firebase-free
 * [RawPushPayload] and hands it to a [PushMessageDispatcher] built from the app's registered
 * [PushMessageHandler] (see [PlatformFirebaseMessaging]). All mapping/dispatch logic lives behind that
 * seam in JVM-unit-tested code; this class is the thin, untestable-off-device adapter.
 *
 * Registered in this module's merged manifest, so a consuming app gets it automatically — it need only
 * supply `google-services.json` and register a handler. With no handler registered, a delivered push
 * is dropped (no crash).
 *
 * `open` so an app can subclass to customize (e.g. override [onNewToken] to persist the token) while
 * reusing the receive path.
 */
public open class PlatformFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val handler = PlatformFirebaseMessaging.resolveHandler(this) ?: return
        val dispatcher =
            PushMessageDispatcher(
                handler = handler,
                logger = PlatformFirebaseMessaging.logger,
                tracerProvider = PlatformFirebaseMessaging.tracerProvider,
            )
        // FCM invokes onMessageReceived on a background thread and keeps the process alive until it
        // returns, so the delivery work must complete before returning — we bridge the non-suspend
        // callback with runBlocking on that thread (never on a caller coroutine), mirroring how
        // analytics-segment's SegmentDeliveryCallback bridges its background delivery callback.
        runBlocking { dispatcher.dispatch(message.toRawPushPayload()) }
    }

    override fun onNewToken(token: String) {
        val handler = PlatformFirebaseMessaging.resolveHandler(this) ?: return
        val dispatcher =
            PushMessageDispatcher(
                handler = handler,
                logger = PlatformFirebaseMessaging.logger,
                tracerProvider = PlatformFirebaseMessaging.tracerProvider,
            )
        runBlocking { dispatcher.dispatchToken(token) }
    }
}

/**
 * Copies a delivered [RemoteMessage] into a [RawPushPayload]. `notification` is null for a data-only
 * "silent" push, so its title/body pass through as null. Kept `internal` and Firebase-typed; the
 * pure-JVM mapping from [RawPushPayload] onward is [PushPayloadMapper].
 */
internal fun RemoteMessage.toRawPushPayload(): RawPushPayload =
    RawPushPayload(
        data = data,
        notificationTitle = notification?.title,
        notificationBody = notification?.body,
        messageId = messageId,
        from = from,
        sentTimeMillis = sentTime,
    )
