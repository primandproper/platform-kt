package com.primandproper.platform.eventstream.android

import com.primandproper.platform.eventstream.Event
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Consume-side coverage for SSE, driven against a real OkHttp `MockWebServer` (no device). */
class SseEventSourceTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun request(): Request = Request.Builder().url(server.url("/events")).build()

    @Test
    fun `reads typed events and reassembles their payloads`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        "event: a\ndata: {\"x\":1}\n\n" +
                            "event: b\ndata: {\"y\":2}\n\n",
                    ),
            )

            val events = SseEventSource(OkHttpClient()).events(request()).take(2).toList()

            assertEquals(Event("a", """{"x":1}"""), events[0])
            assertEquals(Event("b", """{"y":2}"""), events[1])
        }

    @Test
    fun `an event without a type surfaces an empty type`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"only\":\"data\"}\n\n"),
            )

            val event = SseEventSource(OkHttpClient()).events(request()).take(1).toList().single()

            assertEquals("", event.type)
            assertEquals("""{"only":"data"}""", event.payload)
        }

    @Test
    fun `messages carry the SSE id so a caller can resume with Last-Event-ID`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("id: 42\nevent: a\ndata: {\"x\":1}\n\n"),
            )

            val message = SseEventSource(OkHttpClient()).messages(request()).take(1).toList().single()

            assertEquals("42", message.id)
            assertEquals(Event("a", """{"x":1}"""), message.event)
        }

    @Test
    fun `a non-2xx response fails the stream instead of completing normally`() =
        runTest {
            // okhttp-sse reports this as onFailure(t = null); the source must surface it as an error.
            server.enqueue(MockResponse().setResponseCode(500))

            assertFailsWith<IOException> {
                SseEventSource(OkHttpClient()).events(request()).toList()
            }
        }
}
