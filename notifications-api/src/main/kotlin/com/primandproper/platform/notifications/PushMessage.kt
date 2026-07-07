package com.primandproper.platform.notifications

/**
 * The content of a single outbound push notification. Port of platform-go's `mobile.PushMessage`,
 * widened with the [data] payload that FCM/APNs carry alongside the visible alert.
 *
 * [title] and [body] are the user-visible alert. [data] is the silent key/value payload delivered to
 * the app (custom routing keys, deep-link ids, …); FCM requires string values, so it is typed
 * `Map<String, String>`. [badgeCount] is optional and iOS-only: when non-null it sets the app icon
 * badge (APNs `aps.badge`). It has no effect on the FCM/Android send path — Android has no OS-level
 * badge in an FCM notification — so a backend on that path drops it (and logs that it did), exactly as
 * platform-go's `MultiPlatformPushSender` does.
 */
public data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val badgeCount: Int? = null,
)
