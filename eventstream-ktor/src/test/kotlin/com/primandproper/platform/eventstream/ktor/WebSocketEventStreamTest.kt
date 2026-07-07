package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventCodec
import com.primandproper.platform.eventstream.WebSocketConfig
import com.primandproper.platform.observability.testing.RecordingObserver
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

/** Port of platform-go's `eventstream/websocket/websocket_test.go`, driven through Ktor's `testApplication`. */
class WebSocketEventStreamTest {
    @Test
    fun `send-only stream emits the JSON envelope and observes the send`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamWebSockets()
                routing {
                    webSocketEventStream("/ws", obs) { stream ->
                        stream.send(Event("greeting", """{"hello":"world"}"""))
                        stream.close()
                    }
                }
            }

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/ws") {
                val frame = incoming.receive() as Frame.Text
                val event = EventCodec.decode(frame.readText())
                assertEquals("greeting", event.type)
                assertEquals("""{"hello":"world"}""", event.payload)
            }

            val op = obs.operations.last { it.name == "ws_send" }
            assertEquals("greeting", op.values["event.type"])
        }

    @Test
    fun `bidirectional stream receives an inbound event and can reply`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamWebSockets()
                routing {
                    bidirectionalWebSocketEventStream("/bidi", obs) { stream ->
                        val first = stream.receive().first()
                        stream.send(Event("echo", first.payload))
                        stream.close()
                    }
                }
            }

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/bidi") {
                send(Frame.Text(EventCodec.encode(Event("ping", """{"n":1}"""))))
                val reply = EventCodec.decode((incoming.receive() as Frame.Text).readText())
                assertEquals("echo", reply.type)
                assertEquals("""{"n":1}""", reply.payload)
            }
        }

    @Test
    fun `bidirectional read skips a malformed frame and continues`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamWebSockets()
                routing {
                    bidirectionalWebSocketEventStream("/skip", obs) { stream ->
                        val first = stream.receive().first()
                        stream.send(Event("got", first.payload))
                        stream.close()
                    }
                }
            }

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/skip") {
                // First frame is not valid JSON and must be skipped.
                send(Frame.Text("this is not json"))
                // Second frame is valid; receiving the echo proves the read loop continued past the bad one.
                send(Frame.Text(EventCodec.encode(Event("good", """{"ok":true}"""))))

                val reply = EventCodec.decode((incoming.receive() as Frame.Text).readText())
                assertEquals("got", reply.type)
                assertEquals("""{"ok":true}""", reply.payload)
            }
        }

    @Test
    fun `a disallowed origin is rejected before the handler runs`() =
        testApplication {
            val obs = RecordingObserver()
            val config = WebSocketConfig(allowedOrigins = listOf("https://allowed.example"))
            application {
                installEventStreamWebSockets()
                routing {
                    webSocketEventStream("/guarded", obs, config) { stream ->
                        stream.send(Event("should-not-arrive"))
                        stream.close()
                    }
                }
            }

            val wsClient = createClient { install(WebSockets) }
            wsClient.webSocket("/guarded", request = { header(HttpHeaders.Origin, "https://evil.example") }) {
                val reason = closeReason.await()
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
            }
        }
}
