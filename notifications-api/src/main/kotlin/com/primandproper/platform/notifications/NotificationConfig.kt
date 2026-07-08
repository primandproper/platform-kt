package com.primandproper.platform.notifications

/**
 * The supported push-notification providers. Port of platform-go's `notifications/mobile/config`
 * `Provider*` constants. [value] is the wire/string form validated against configuration.
 *
 * [APNS_FCM] selects the real APNs (iOS) + FCM (Android) senders — only the FCM half is implemented
 * in this port (`:notifications-fcm`); APNs is a documented `TODO(apns)` seam. [NOOP] selects the
 * discard-everything sender.
 */
public enum class NotificationProvider(
    public val value: String,
) {
    APNS_FCM("apns_fcm"),
    NOOP("noop"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's config validation rejecting an unknown name.
         */
        public fun fromValue(value: String): NotificationProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/** Thrown when [NotificationConfig.validate] is given a non-empty provider that names no known backend. */
public class InvalidNotificationProviderException(
    provider: String,
) : IllegalArgumentException("unknown notification provider: $provider")

/**
 * Provider-agnostic push-notification configuration. Port of the portable part of platform-go's
 * `notifications/mobile/config.Config`: the chosen [provider].
 *
 * The per-provider connection settings (FCM project id / Bearer token, APNs key paths) live with the
 * backend modules (`FcmConfig` in `:notifications-fcm`), so this API module stays transport-free —
 * matching how `:email-api` keeps the Resend settings in `:email-resend`. Consequently [validate]
 * here checks only that a non-empty [provider] names a known backend; the "provider X requires its
 * config block" check that platform-go performs in `ValidateWithContext` happens at the wiring layer,
 * where the concrete per-provider config is in scope.
 *
 * An empty [provider] is permitted and selects the noop sender, mirroring Go's `ProvidePushSender`
 * default branch.
 */
public data class NotificationConfig(
    val provider: String = "",
) {
    /** Throws [InvalidNotificationProviderException] when [provider] is non-empty yet unknown. */
    public fun validate() {
        if (provider.isBlank()) return
        NotificationProvider.fromValue(provider) ?: throw InvalidNotificationProviderException(provider)
    }
}
