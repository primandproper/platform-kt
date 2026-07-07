package com.primandproper.platform.observability.otel

import com.primandproper.platform.observability.Span
import com.primandproper.platform.observability.Tracer
import com.primandproper.platform.observability.TracerProvider
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.util.concurrent.TimeUnit

private const val SERVICE_NAME_KEY = "service.name"
private const val FLUSH_TIMEOUT_SECONDS = 10L

internal class OtelTracer(
    private val delegate: io.opentelemetry.api.trace.Tracer,
) : Tracer {
    // spanBuilder parents to Context.current() by default, which is what asCoroutineContextElement
    // installs — so nested spans link up without any explicit parent threading.
    override fun startSpan(name: String): Span = delegate.spanBuilder(name).startSpan()
}

/**
 * The OpenTelemetry-backed [TracerProvider]: an [OpenTelemetrySdk] with an OTLP exporter, head
 * sampling, and W3C trace-context propagation. Port of platform-go's `oteltrace.SetupOtelGRPC`.
 *
 * Deliberately does not touch `GlobalOpenTelemetry` — propagation runs through the standalone OTel
 * `Context` API, so there's no global to pollute (and tests stay independent).
 */
public class OtelTracerProvider internal constructor(
    private val sdk: OpenTelemetrySdk,
) : TracerProvider {
    override fun tracer(name: String): Tracer = OtelTracer(sdk.getTracer(name))

    override fun forceFlush() {
        sdk.sdkTracerProvider.forceFlush().join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun shutdown() {
        sdk.sdkTracerProvider.shutdown().join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    public companion object {
        /**
         * Builds a provider exporting over OTLP to [endpoint]. [sampleRatio] is parent-based head
         * sampling in `[0, 1]`. Set [useHttp] for OTLP/HTTP instead of gRPC.
         */
        public fun create(
            serviceName: String,
            endpoint: String,
            sampleRatio: Double = 1.0,
            useHttp: Boolean = false,
        ): OtelTracerProvider {
            val exporter: SpanExporter =
                if (useHttp) {
                    OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build()
                } else {
                    OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build()
                }
            return create(serviceName, exporter, sampleRatio)
        }

        /** Builds a provider around a pre-constructed [exporter] — the seam tests use to inject an in-memory exporter. */
        public fun create(
            serviceName: String,
            exporter: SpanExporter,
            sampleRatio: Double = 1.0,
        ): OtelTracerProvider {
            val resource =
                Resource.getDefault().merge(
                    Resource.create(Attributes.of(AttributeKey.stringKey(SERVICE_NAME_KEY), serviceName)),
                )

            val sdkTracerProvider =
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(sampleRatio)))
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                    .build()

            val sdk =
                OpenTelemetrySdk.builder()
                    .setTracerProvider(sdkTracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build()

            return OtelTracerProvider(sdk)
        }
    }
}
