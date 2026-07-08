package com.primandproper.platform.eventstream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers the JSON envelope port of Go's `conn.WriteJSON` / `json.Unmarshal`. */
class EventCodecTest {
    @Test
    fun `encode nests the raw payload under the envelope`() {
        val encoded = EventCodec.encode(Event("test", """{"msg":"hi"}"""))
        assertEquals(Event("test", """{"msg":"hi"}"""), EventCodec.decode(encoded))
    }

    @Test
    fun `encode omits a null payload`() {
        assertEquals("""{"type":"ping"}""", EventCodec.encode(Event("ping")))
    }

    @Test
    fun `decode returns empty type and null payload for a bare object`() {
        assertEquals(Event("", null), EventCodec.decode("{}"))
    }

    @Test
    fun `decode preserves a scalar payload as raw JSON`() {
        val event = EventCodec.decode("""{"type":"n","payload":42}""")
        assertEquals("n", event.type)
        assertEquals("42", event.payload)
    }

    @Test
    fun `round trip preserves an object payload byte-for-byte`() {
        val original = Event("update", """{"id":"abc","status":"done"}""")
        assertEquals(original, EventCodec.decode(EventCodec.encode(original)))
    }

    @Test
    fun `decodeEvents drops malformed elements and keeps the rest`() =
        runTest {
            val events =
                flowOf(
                    """{"type":"good","payload":{"ok":true}}""",
                    "this is not json",
                    """{"type":"also-good"}""",
                ).decodeEvents().toList()

            assertEquals(2, events.size)
            assertEquals("good", events[0].type)
            assertEquals("also-good", events[1].type)
        }
}
