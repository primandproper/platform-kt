package com.primandproper.platform.eventstream.ktor

import com.primandproper.platform.eventstream.Event
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `eventstream/sse/sse_test.go`, driven through Ktor's `testApplication`. */
class SseEventStreamTest {
    @Test
    fun `emits a typed event in SSE format and observes the send`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamSse()
                routing {
                    sseEventStream("/events", obs) { stream ->
                        stream.send(Event("test", """{"msg":"hello"}"""))
                        stream.close()
                    }
                }
            }

            val body = client.get("/events").bodyAsText()
            assertTrue(Regex("event:\\s*test").containsMatchIn(body), body)
            assertTrue(Regex("""data:\s*\{"msg":"hello"\}""").containsMatchIn(body), body)

            val op = obs.operations.last { it.name == "sse_send" }
            assertEquals("test", op.values["event.type"])
            assertEquals("""{"msg":"hello"}""".length, op.values[Keys.LENGTH])
            assertTrue(op.errors.isEmpty())
        }

    @Test
    fun `an event without a type emits only data lines`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamSse()
                routing {
                    sseEventStream("/events", obs) { stream ->
                        stream.send(Event(payload = """{"x":1}"""))
                        stream.close()
                    }
                }
            }

            val body = client.get("/events").bodyAsText()
            assertTrue(Regex("""data:\s*\{"x":1\}""").containsMatchIn(body), body)
            assertFalse(body.contains("event:"), body)
        }

    @Test
    fun `multiple events each frame as their own SSE block`() =
        testApplication {
            val obs = RecordingObserver()
            application {
                installEventStreamSse()
                routing {
                    sseEventStream("/events", obs) { stream ->
                        for (name in listOf("first", "second", "third")) {
                            stream.send(Event("msg", "\"$name\""))
                        }
                        stream.close()
                    }
                }
            }

            val body = client.get("/events").bodyAsText()
            assertEquals(3, Regex("event:\\s*msg").findAll(body).count(), body)
            assertTrue(Regex("""data:\s*"first"""").containsMatchIn(body), body)
            assertTrue(Regex("""data:\s*"third"""").containsMatchIn(body), body)
        }
}
