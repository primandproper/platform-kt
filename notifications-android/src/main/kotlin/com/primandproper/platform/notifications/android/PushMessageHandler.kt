package com.primandproper.platform.notifications.android

/**
 * The app's hook for reacting to received pushes — what an Android app implements to handle an
 * incoming notification (post a system notification, refresh data, deep-link, …). The receive-side
 * analog of the server's `PushNotificationSender`: the server SENDS, the device HANDLES.
 *
 * [PushMessageDispatcher] maps a delivered payload into an [IncomingPushMessage] and invokes
 * [onMessageReceived]; a token rotation invokes [onNewToken]. Both suspend so a handler can do async
 * work (DB writes, network refresh) with structured concurrency.
 */
public interface PushMessageHandler {
    /** Called with each successfully-mapped incoming push. */
    public suspend fun onMessageReceived(message: IncomingPushMessage)

    /** Called when FCM issues a new registration token for this device. Defaults to a no-op. */
    public suspend fun onNewToken(token: String) {
    }
}
