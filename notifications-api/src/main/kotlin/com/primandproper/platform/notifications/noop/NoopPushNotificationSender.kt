package com.primandproper.platform.notifications.noop

import com.primandproper.platform.notifications.PushMessage
import com.primandproper.platform.notifications.PushNotificationSender

/**
 * A [PushNotificationSender] that discards every message — the port of platform-go's
 * `notifications/mobile/noop.pushNotificationSender`. The safe default the config factory falls back
 * to when no provider is selected, and for tests that don't care about real delivery.
 */
public class NoopPushNotificationSender : PushNotificationSender {
    override suspend fun sendPush(
        platform: String,
        token: String,
        message: PushMessage,
    ) {
    }

    override suspend fun sendToTopic(
        platform: String,
        topic: String,
        message: PushMessage,
    ) {
    }
}
