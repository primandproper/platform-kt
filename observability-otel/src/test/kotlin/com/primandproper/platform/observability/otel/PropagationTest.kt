package com.primandproper.platform.observability.otel

import com.primandproper.platform.observability.DefaultObserverFactory
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.span
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PropagationTest {

    /**
     * The whole reason for the CoroutineContext bridge: a span opened in an outer `span { }` must be
     * the parent of a span opened in a nested suspend call, even after the coroutine hops to a
     * different dispatcher thread.
     */
    @Test
    fun innerSpanParentsUnderOuterAcrossDispatchers() = runTest {
        val exporter = InMemorySpanExporter.create()
        val provider = OtelTracerProvider.create(serviceName = "test", exporter = exporter, sampleRatio = 1.0)
        val o11y = DefaultObserverFactory(NoopLogger, provider).named("component")

        o11y.span("outer") {
            withContext(Dispatchers.Default) {
                o11y.span("inner") {
                    set("worked" to true)
                }
            }
        }

        provider.forceFlush()
        val spans = exporter.finishedSpanItems
        val outer = spans.single { it.name == "outer" }
        val inner = spans.single { it.name == "inner" }

        assertEquals(outer.spanId, inner.parentSpanId, "inner span must parent under outer")
        assertEquals(outer.traceId, inner.traceId, "inner span must share the outer trace")

        provider.shutdown()
    }
}
