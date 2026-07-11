package com.primandproper.platform.notifications

import com.primandproper.platform.notifications.mock.PushNotificationSenderMock
import com.primandproper.platform.notifications.noop.NoopPushNotificationSender
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Covers the noop and mock `PushNotificationSender` doubles that ship with the api module. */
class PushNotificationSenderDoublesTest {
    private val message = PushMessage(title = "t", body = "b", data = mapOf("k" to "v"))

    @Test
    fun `noop discards every call`() =
        runTest {
            val noop = NoopPushNotificationSender
            noop.sendPush(Platform.ANDROID, "token", message)
            noop.sendToTopic(Platform.ANDROID, "topic", message)
        }

    @Test
    fun `mock records sendPush arguments and delegates to the func`() =
        runTest {
            var seen: PushMessage? = null
            val mock = PushNotificationSenderMock(sendPushFunc = { _, _, m -> seen = m })

            mock.sendPush(Platform.ANDROID, "device-token", message)

            assertEquals(1, mock.sendPushCalls.size)
            assertEquals(Platform.ANDROID, mock.sendPushCalls.single().platform)
            assertEquals("device-token", mock.sendPushCalls.single().token)
            assertEquals(message, seen)
        }

    @Test
    fun `mock records sendToTopic arguments`() =
        runTest {
            val mock = PushNotificationSenderMock(sendToTopicFunc = { _, _, _ -> })

            mock.sendToTopic(Platform.ANDROID, "news", message)

            assertEquals("news", mock.sendToTopicCalls.single().topic)
        }

    @Test
    fun `an unmocked call surfaces immediately`() =
        runTest {
            val mock = PushNotificationSenderMock()
            assertFailsWith<IllegalStateException> { mock.sendPush(Platform.ANDROID, "t", message) }
        }
}
