package com.primandproper.platform.notifications

/** The platform token for iOS devices, routed to APNs. Mirrors platform-go's `platformIOS`. */
public const val PLATFORM_IOS: String = "ios"

/** The platform token for Android devices, routed to FCM. Mirrors platform-go's `platformAndroid`. */
public const val PLATFORM_ANDROID: String = "android"

/**
 * Thrown when a push is addressed to a platform that has no configured sender (e.g. an iOS token when
 * APNs is not wired). Port of platform-go's `mobile.ErrPlatformNotSupported`.
 */
public class PlatformNotSupportedException(
    public val platform: String,
) : IllegalStateException("push notifications not configured for platform \"$platform\"")

/**
 * A service that SENDS push notifications to devices or topics — the port of platform-go's
 * `mobile.PushNotificationSender`.
 *
 * DIRECTION: this is the *server* side. A backend routes by [platform] — APNs for `ios`, FCM for
 * `android` — and delivers to a device token or a subscription topic. Go's methods thread a
 * `context.Context` and return an `error`; this port suspends instead (cancellation and trace context
 * ride the coroutine context) and signals failure by throwing, the idiomatic Kotlin shape.
 *
 * Backends live in sibling modules (`:notifications-fcm`); the noop and mock doubles ship here. The
 * inbound RECEIVE side — an Android app reacting to a delivered push — is the direction-flipped
 * `:notifications-android` module (port of Go's `mobile` package as consumed on-device).
 */
public interface PushNotificationSender {
    /**
     * Sends [message] to a single device [token] on [platform] (`ios`/`android`), throwing on
     * failure. Throws [PlatformNotSupportedException] when the backend has no sender for [platform].
     */
    public suspend fun sendPush(
        platform: String,
        token: String,
        message: PushMessage,
    )

    /**
     * Sends [message] to every device subscribed to [topic] on [platform], throwing on failure.
     * Topic fan-out is an FCM capability (APNs has no equivalent), so backends without topic support
     * throw [PlatformNotSupportedException].
     */
    public suspend fun sendToTopic(
        platform: String,
        topic: String,
        message: PushMessage,
    )
}
