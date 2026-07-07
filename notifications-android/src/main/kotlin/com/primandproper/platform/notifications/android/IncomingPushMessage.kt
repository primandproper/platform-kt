package com.primandproper.platform.notifications.android

/**
 * A push notification as RECEIVED on-device — the direction-flipped counterpart of the server's
 * outbound `PushMessage`. Loosely the inbound analog of platform-go's `mobile.MobileNotificationRequest`
 * (which the server formats and the device consumes): [requestType] routes the message to a handler,
 * [title]/[body] are the visible alert, and [data] carries the remaining context key/values.
 *
 * DIRECTION: the send side (`:notifications-fcm`) builds an FCM `message`; the FCM infrastructure
 * delivers it to this device; a `FirebaseMessagingService` (the `TODO(fcm-android)` seam) hands the
 * delivered `RemoteMessage` to [PushMessageDispatcher], which maps it into this type via
 * [PushPayloadMapper]. The correlation fields ([messageId], [from], [sentTimeMillis]) come straight
 * off the delivered message.
 */
public data class IncomingPushMessage(
    val title: String,
    val body: String,
    val requestType: String = "",
    val data: Map<String, String> = emptyMap(),
    val messageId: String = "",
    val from: String = "",
    val sentTimeMillis: Long = 0,
)
