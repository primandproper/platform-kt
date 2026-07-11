package com.primandproper.platform.httpclient

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RetryTest {
    @Test
    fun `no hook runs a single attempt`() =
        runTest {
            val cfg = HttpClientConfig()
            var calls = 0
            val response =
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    HttpResponse(statusCode = 200)
                }
            assertEquals(1, calls)
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `hook retries until it returns null`() =
        runTest {
            val cfg =
                HttpClientConfig(
                    retryHook =
                        RetryHook { attempt, _, outcome ->
                            val status = (outcome as HttpOutcome.Received).response.statusCode
                            if (status == 503 && attempt < 3) Duration.ZERO else null
                        },
                )
            var calls = 0
            val response =
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    if (calls < 3) HttpResponse(statusCode = 503) else HttpResponse(statusCode = 200)
                }
            assertEquals(3, calls)
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `hook can retry a thrown failure then rethrow`() =
        runTest {
            val cfg =
                HttpClientConfig(
                    retryHook =
                        RetryHook { attempt, _, outcome ->
                            if (outcome is HttpOutcome.Failed && attempt < 2) 1.milliseconds else null
                        },
                )
            var calls = 0
            assertFailsWith<IllegalStateException> {
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    throw IllegalStateException("boom")
                }
            }
            assertEquals(2, calls)
        }

    @Test
    fun `records a retry event per attempt and the total retry count on the span`() =
        runTest {
            val exporter = InMemorySpanExporter.create()
            val tracerProvider =
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build()
            val tracer = tracerProvider.get("test")

            val cfg =
                HttpClientConfig(
                    retryHook =
                        RetryHook { attempt, _, outcome ->
                            if (outcome is HttpOutcome.Failed && attempt < 3) Duration.ZERO else null
                        },
                )

            val span = tracer.spanBuilder("call").startSpan()
            var calls = 0
            span.makeCurrent().use {
                assertFailsWith<IllegalStateException> {
                    cfg.executeWithRetries(HttpRequest.get("https://x")) {
                        calls++
                        throw IllegalStateException("boom $calls")
                    }
                }
            }
            span.end()

            val finished = exporter.finishedSpanItems.single()
            // Attempts 1 and 2 were retried; attempt 3 gave up — so two retry events, retry_count 2.
            val retryEvents = finished.events.filter { it.name == "http.retry" }
            assertEquals(2, retryEvents.size)
            assertEquals(1L, retryEvents.first().attributes.get(AttributeKey.longKey("http.retry_attempt")))
            assertEquals(
                "java.lang.IllegalStateException",
                retryEvents.first().attributes.get(AttributeKey.stringKey("exception.type")),
            )
            assertEquals(2L, finished.attributes.get(AttributeKey.longKey("http.retry_count")))

            tracerProvider.shutdown()
        }
}
