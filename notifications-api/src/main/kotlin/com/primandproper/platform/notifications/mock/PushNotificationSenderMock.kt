package com.primandproper.platform.notifications.mock

import com.primandproper.platform.notifications.PushMessage
import com.primandproper.platform.notifications.PushNotificationSender

/** A recorded call to [PushNotificationSenderMock.sendPush]. */
public data class SendPushCall(
    val platform: String,
    val token: String,
    val message: PushMessage,
)

/** A recorded call to [PushNotificationSenderMock.sendToTopic]. */
public data class SendToTopicCall(
    val platform: String,
    val topic: String,
    val message: PushMessage,
)

/**
 * A configurable [PushNotificationSender] test double, mirroring platform-go's moq-generated
 * `PushNotificationSenderMock`. Each method delegates to a settable func; calling one while its func
 * is `null` throws [IllegalStateException], the same "unmocked call surfaces immediately" behavior
 * moq's generated panic gives. Every call's arguments are recorded in the corresponding calls list,
 * standing in for moq's generated `*Calls()` accessors.
 *
 * ```
 * val mock = PushNotificationSenderMock(sendPushFunc = { _, _, _ -> /* assert on it */ })
 * ```
 */
public class PushNotificationSenderMock(
    public var sendPushFunc: (suspend (platform: String, token: String, message: PushMessage) -> Unit)? = null,
    public var sendToTopicFunc: (suspend (platform: String, topic: String, message: PushMessage) -> Unit)? = null,
) : PushNotificationSender {
    /** Every [sendPush] call, in order. */
    public val sendPushCalls: MutableList<SendPushCall> = mutableListOf()

    /** Every [sendToTopic] call, in order. */
    public val sendToTopicCalls: MutableList<SendToTopicCall> = mutableListOf()

    override suspend fun sendPush(
        platform: String,
        token: String,
        message: PushMessage,
    ) {
        sendPushCalls += SendPushCall(platform, token, message)
        val func = sendPushFunc ?: error("mock.sendPushFunc: method is null but was just called")
        func(platform, token, message)
    }

    override suspend fun sendToTopic(
        platform: String,
        topic: String,
        message: PushMessage,
    ) {
        sendToTopicCalls += SendToTopicCall(platform, topic, message)
        val func = sendToTopicFunc ?: error("mock.sendToTopicFunc: method is null but was just called")
        func(platform, topic, message)
    }
}
