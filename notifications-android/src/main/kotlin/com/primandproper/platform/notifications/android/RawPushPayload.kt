package com.primandproper.platform.notifications.android

/**
 * The transport-agnostic shape of a delivered push, mirroring the fields a Firebase `RemoteMessage`
 * exposes — kept free of any `com.google.firebase` type so the mapping logic is unit-testable on the
 * JVM without the `firebase-messaging` dependency (the `TODO(fcm-android)` seam).
 *
 * The concrete `FirebaseMessagingService` copies its `RemoteMessage` into this: [data] is
 * `remoteMessage.data`, [notificationTitle]/[notificationBody] are `remoteMessage.notification?.title`
 * / `?.body` (both null for a data-only "silent" push), and [messageId]/[from]/[sentTimeMillis] are
 * the corresponding `RemoteMessage` accessors.
 */
public data class RawPushPayload(
    val data: Map<String, String> = emptyMap(),
    val notificationTitle: String? = null,
    val notificationBody: String? = null,
    val messageId: String? = null,
    val from: String? = null,
    val sentTimeMillis: Long = 0,
)
