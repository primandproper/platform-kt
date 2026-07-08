package com.primandproper.platform.eventstream

import com.primandproper.platform.errors.PlatformException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `eventstream/config/config_test.go` and `websocket/config_test.go`. */
class EventStreamConfigTest {
    @Test
    fun `sse provider resolves`() {
        assertEquals(EventStreamProvider.SSE, EventStreamProvider.fromValue("sse"))
    }

    @Test
    fun `websocket provider resolves case-insensitively and trimmed`() {
        assertEquals(EventStreamProvider.WEBSOCKET, EventStreamProvider.fromValue("  WebSocket "))
    }

    @Test
    fun `invalid provider name resolves to null`() {
        assertNull(EventStreamProvider.fromValue("grpc"))
    }

    @Test
    fun `only websocket supports bidirectional streams`() {
        assertTrue(EventStreamProvider.WEBSOCKET.supportsBidirectional)
        assertFalse(EventStreamProvider.SSE.supportsBidirectional)
    }

    @Test
    fun `sse config resolves without websocket settings`() {
        val cfg = EventStreamConfig(EventStreamProvider.SSE)
        assertEquals(EventStreamProvider.SSE, cfg.resolveProvider())
    }

    @Test
    fun `websocket config requires websocket settings`() {
        val cfg = EventStreamConfig(EventStreamProvider.WEBSOCKET)
        assertFailsWith<PlatformException> { cfg.resolveProvider() }
    }

    @Test
    fun `websocket config with settings resolves`() {
        val cfg = EventStreamConfig(EventStreamProvider.WEBSOCKET, WebSocketConfig())
        assertEquals(EventStreamProvider.WEBSOCKET, cfg.resolveProvider())
    }

    @Test
    fun `websocket config defaults mirror the Go defaults`() {
        val cfg = WebSocketConfig()
        assertEquals(30.seconds, cfg.heartbeatInterval)
        assertEquals(0, cfg.readBufferSize)
        assertTrue(cfg.allowedOrigins.isEmpty())
    }
}
