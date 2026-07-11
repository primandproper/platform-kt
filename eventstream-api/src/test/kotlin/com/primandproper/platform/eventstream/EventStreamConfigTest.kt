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

    @Test
    fun `empty allowlist enforces same-origin and rejects cross-origin`() {
        val cfg = WebSocketConfig()
        // Same host (with matching port) is allowed; a different host is not.
        assertTrue(cfg.isOriginAllowed("https://app.example", host = "app.example"))
        assertTrue(cfg.isOriginAllowed("http://app.example:8080", host = "app.example:8080"))
        assertFalse(cfg.isOriginAllowed("https://evil.example", host = "app.example"))
        // A differing port is a distinct origin and is rejected.
        assertFalse(cfg.isOriginAllowed("https://app.example:9999", host = "app.example"))
    }

    @Test
    fun `empty allowlist matches host case-insensitively`() {
        val cfg = WebSocketConfig()
        assertTrue(cfg.isOriginAllowed("https://App.Example", host = "app.example"))
    }

    @Test
    fun `absent or blank origin is treated as an originless client and allowed`() {
        val cfg = WebSocketConfig()
        assertTrue(cfg.isOriginAllowed(null, host = "app.example"))
        assertTrue(cfg.isOriginAllowed("", host = "app.example"))
        assertTrue(cfg.isOriginAllowed("   ", host = "app.example"))
    }

    @Test
    fun `unparseable or missing-host origin is rejected under same-origin default`() {
        val cfg = WebSocketConfig()
        assertFalse(cfg.isOriginAllowed("app.example", host = "app.example"))
        assertFalse(cfg.isOriginAllowed("https://app.example", host = null))
    }

    @Test
    fun `non-empty allowlist requires an exact origin match`() {
        val cfg = WebSocketConfig(allowedOrigins = listOf("https://allowed.example"))
        assertTrue(cfg.isOriginAllowed("https://allowed.example", host = "app.example"))
        assertFalse(cfg.isOriginAllowed("https://evil.example", host = "allowed.example"))
        // With an explicit allowlist, an absent origin is still an originless client (allowed).
        assertTrue(cfg.isOriginAllowed(null, host = "app.example"))
    }
}
