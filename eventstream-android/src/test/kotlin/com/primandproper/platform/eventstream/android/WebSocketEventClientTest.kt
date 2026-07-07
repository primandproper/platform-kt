package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.eventstream.EventCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Consume/reply coverage for the WebSocket client, driven against a real `MockWebServer` upgrade. */
class WebSocketEventClientTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        // Dispose the client first: an undisposed dispatcher/connection pool keeps the WebSocket
        // upgrade alive, so MockWebServer.shutdown() would time out ("gave up waiting for queue").
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    private fun request(): Request = Request.Builder().url(server.url("/ws")).build()

    @Test
    fun `receives an inbound event as a decoded Event`() =
        runBlocking {
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            webSocket.send(EventCodec.encode(Event("srv", """{"a":1}""")))
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = webSocket.close(code, reason).let { }
                    },
                ),
            )

            val session = WebSocketEventClient(client).open(request())
            val event = session.incoming.first()

            assertEquals(Event("srv", """{"a":1}"""), event)
            session.close()
            // Await the close handshake (onClosed closes the channel) so MockWebServer's server-side
            // reader task ends before tearDown's shutdown — otherwise its queue times out.
            withTimeoutOrNull(5_000) { session.incoming.collect { } }
            Unit // keep the test's return type Unit (JUnit rejects a Unit? -returning @Test)
        }

    @Test
    fun `send delivers the JSON envelope to the server`() =
        runBlocking {
            val received = CompletableDeferred<String>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            received.complete(text)
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = webSocket.close(code, reason).let { }
                    },
                ),
            )

            val session = WebSocketEventClient(client).open(request())
            session.send(Event("cli", """{"b":2}"""))

            val text = received.await()
            assertEquals(Event("cli", """{"b":2}"""), EventCodec.decode(text))
            session.close()
            // Await the close handshake (onClosed closes the channel) so MockWebServer's server-side
            // reader task ends before tearDown's shutdown — otherwise its queue times out.
            withTimeoutOrNull(5_000) { session.incoming.collect { } }
            Unit // keep the test's return type Unit (JUnit rejects a Unit? -returning @Test)
        }

    @Test
    fun `skips a malformed inbound frame and delivers the next valid one`() =
        runBlocking {
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            webSocket.send("this is not json")
                            webSocket.send(EventCodec.encode(Event("good", """{"ok":true}""")))
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = webSocket.close(code, reason).let { }
                    },
                ),
            )

            val session = WebSocketEventClient(client).open(request())
            val event = session.incoming.first()

            assertEquals(Event("good", """{"ok":true}"""), event)
            session.close()
            // Await the close handshake (onClosed closes the channel) so MockWebServer's server-side
            // reader task ends before tearDown's shutdown — otherwise its queue times out.
            withTimeoutOrNull(5_000) { session.incoming.collect { } }
            Unit // keep the test's return type Unit (JUnit rejects a Unit? -returning @Test)
        }
}
