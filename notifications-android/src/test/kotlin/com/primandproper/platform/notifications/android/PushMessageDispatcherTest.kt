package com.primandproper.platform.notifications.android

import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the receive-side dispatch flow: mapping, handler invocation, malformed-drop, and observability. */
class PushMessageDispatcherTest {
    private class RecordingHandler : PushMessageHandler {
        val received = mutableListOf<IncomingPushMessage>()
        var lastToken: String? = null

        override suspend fun onMessageReceived(message: IncomingPushMessage) {
            received += message
        }

        override suspend fun onNewToken(token: String) {
            lastToken = token
        }
    }

    private fun dispatcher(): Triple<PushMessageDispatcher, RecordingHandler, RecordingObserver> {
        val handler = RecordingHandler()
        val obs = RecordingObserver()
        return Triple(PushMessageDispatcher(obs, handler), handler, obs)
    }

    @Test
    fun `a usable payload is mapped and handed to the handler`() =
        runTest {
            val (dispatcher, handler, obs) = dispatcher()

            val handled = dispatcher.dispatch(RawPushPayload(notificationTitle = "Hi", notificationBody = "there", messageId = "m1"))

            assertTrue(handled)
            assertEquals("Hi", handler.received.single().title)
            obs.assertObservedOperationWithValues(
                NotificationKeys.TITLE to "Hi",
                NotificationKeys.MESSAGE_ID to "m1",
            )
        }

    @Test
    fun `a malformed payload is dropped and the handler is not called`() =
        runTest {
            val (dispatcher, handler, _) = dispatcher()

            val handled = dispatcher.dispatch(RawPushPayload())

            assertFalse(handled)
            assertTrue(handler.received.isEmpty())
        }

    @Test
    fun `a message id is only recorded when present`() =
        runTest {
            val (dispatcher, _, obs) = dispatcher()

            dispatcher.dispatch(RawPushPayload(notificationTitle = "Hi"))

            assertNull(obs.operations.single().values[NotificationKeys.MESSAGE_ID])
        }

    @Test
    fun `dispatchToken forwards a rotated token to the handler`() =
        runTest {
            val (dispatcher, handler, _) = dispatcher()

            dispatcher.dispatchToken("new-token")

            assertEquals("new-token", handler.lastToken)
        }
}
