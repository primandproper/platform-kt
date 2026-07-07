package com.primandproper.platform.notifications.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises the payload -> model mapping, including malformed payloads. */
class PushPayloadMapperTest {
    @Test
    fun `a notification push maps title and body from the notification block`() {
        val payload =
            RawPushPayload(
                notificationTitle = "Hello",
                notificationBody = "World",
                messageId = "0:123",
                from = "/topics/news",
                sentTimeMillis = 42,
            )

        val message = PushPayloadMapper.map(payload)!!

        assertEquals("Hello", message.title)
        assertEquals("World", message.body)
        assertEquals("0:123", message.messageId)
        assertEquals("/topics/news", message.from)
        assertEquals(42, message.sentTimeMillis)
    }

    @Test
    fun `a data-only push falls back to the title and body data keys`() {
        val payload =
            RawPushPayload(
                data = mapOf("title" to "DataTitle", "body" to "DataBody", "requestType" to "chat", "roomId" to "r1"),
            )

        val message = PushPayloadMapper.map(payload)!!

        assertEquals("DataTitle", message.title)
        assertEquals("DataBody", message.body)
        assertEquals("chat", message.requestType)
        // The reserved keys are lifted out; only the remaining context stays in data.
        assertEquals(mapOf("roomId" to "r1"), message.data)
    }

    @Test
    fun `the notification block wins over the data keys`() {
        val payload =
            RawPushPayload(
                notificationTitle = "FromNotification",
                data = mapOf("title" to "FromData", "body" to "DataBody"),
            )

        val message = PushPayloadMapper.map(payload)!!

        assertEquals("FromNotification", message.title)
        assertEquals("DataBody", message.body)
    }

    @Test
    fun `a silent push with context but no alert is valid`() {
        val payload = RawPushPayload(data = mapOf("requestType" to "sync", "cursor" to "abc"))

        val message = PushPayloadMapper.map(payload)!!

        assertEquals("", message.title)
        assertEquals("", message.body)
        assertEquals("sync", message.requestType)
        assertEquals(mapOf("cursor" to "abc"), message.data)
    }

    @Test
    fun `a completely empty payload is malformed and maps to null`() {
        assertNull(PushPayloadMapper.map(RawPushPayload()))
    }

    @Test
    fun `blank notification values are treated as absent`() {
        val payload = RawPushPayload(notificationTitle = "   ", notificationBody = "")

        // No usable alert and no data -> malformed.
        assertNull(PushPayloadMapper.map(payload))
    }

    @Test
    fun `null correlation fields default to empty`() {
        val message = PushPayloadMapper.map(RawPushPayload(notificationTitle = "t"))!!

        assertEquals("", message.messageId)
        assertEquals("", message.from)
        assertEquals("", message.requestType)
    }
}
