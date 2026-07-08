package com.primandproper.platform.notifications

/**
 * The standard observability attribute keys for push notifications, so a value is named identically
 * wherever it is recorded across backends. Port of the notification-relevant keys platform-go's
 * senders set on their operations (`op.Set("platform", …)`, `op.Set("title", …)`,
 * `op.Set("fcm.message_id", …)`).
 *
 * REDACTION: platform-go's FCM/APNs senders never put the device token on a span — they record the
 * title, the platform, and the provider-assigned message id, but not the credential-bearing token —
 * so this port carries the same behavior: there is no `token` key here and backends do not record it.
 * The Bearer auth header a backend sends is likewise redacted by `:httpclient-api`'s HeaderRedaction
 * before any request data reaches a span.
 */
public object NotificationKeys {
    /** The target platform (`ios`/`android`). Mirrors Go's `op.Set("platform", …)`. */
    public const val PLATFORM: String = "notification.platform"

    /** The notification's visible title. Mirrors Go's `op.Set("title", …)`. */
    public const val TITLE: String = "notification.title"

    /** The subscription topic a fan-out send targets. */
    public const val TOPIC: String = "notification.topic"

    /** The provider-assigned message identifier returned by a successful send. Mirrors Go's `"fcm.message_id"`. */
    public const val MESSAGE_ID: String = "notification.message_id"

    /** The async channel an event is published to. */
    public const val CHANNEL: String = "notification.channel"

    /** The async event's type discriminator. */
    public const val EVENT_TYPE: String = "notification.event_type"
}
