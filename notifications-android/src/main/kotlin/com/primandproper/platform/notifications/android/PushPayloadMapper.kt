package com.primandproper.platform.notifications.android

/**
 * Maps a delivered [RawPushPayload] into an [IncomingPushMessage] — the pure, side-effect-free core of
 * the receive side, so the mapping (including how malformed payloads are handled) is unit-testable
 * without DataStore, Firebase, or a device.
 *
 * Resolution rules (mirroring how an FCM app reads a `RemoteMessage`):
 * - A visible-alert title/body from the `notification` block wins; a data-only ("silent") push falls
 *   back to the [DATA_TITLE]/[DATA_BODY] data keys. Blank values are treated as absent.
 * - [DATA_REQUEST_TYPE] is lifted out of the data bag into [IncomingPushMessage.requestType] (the
 *   routing discriminator, port of Go's `MobileNotificationRequest.RequestType`); the remaining data
 *   keys become the message's [IncomingPushMessage.data] context.
 *
 * MALFORMED: a payload that carries neither a visible alert (no notification title/body) nor any data
 * cannot be turned into a usable message; [map] returns `null` for it rather than fabricating an empty
 * notification, so [PushMessageDispatcher] can drop it. (A data-only push with context but no
 * title/body is valid — that is a legitimate silent push — and maps successfully.)
 */
public object PushPayloadMapper {
    /** Data key carrying the title for a data-only push. */
    public const val DATA_TITLE: String = "title"

    /** Data key carrying the body for a data-only push. */
    public const val DATA_BODY: String = "body"

    /** Data key carrying the routing discriminator. Port of Go's `MobileNotificationRequest.RequestType`. */
    public const val DATA_REQUEST_TYPE: String = "requestType"

    private val reservedKeys = setOf(DATA_TITLE, DATA_BODY, DATA_REQUEST_TYPE)

    /** Maps [payload] to an [IncomingPushMessage], or `null` when the payload carries nothing usable. */
    public fun map(payload: RawPushPayload): IncomingPushMessage? {
        val title = payload.notificationTitle?.takeIf { it.isNotBlank() } ?: payload.data[DATA_TITLE]?.takeIf { it.isNotBlank() }
        val body = payload.notificationBody?.takeIf { it.isNotBlank() } ?: payload.data[DATA_BODY]?.takeIf { it.isNotBlank() }

        if (title == null && body == null && payload.data.isEmpty()) return null

        return IncomingPushMessage(
            title = title.orEmpty(),
            body = body.orEmpty(),
            requestType = payload.data[DATA_REQUEST_TYPE].orEmpty(),
            data = payload.data.filterKeys { it !in reservedKeys },
            messageId = payload.messageId.orEmpty(),
            from = payload.from.orEmpty(),
            sentTimeMillis = payload.sentTimeMillis,
        )
    }
}
