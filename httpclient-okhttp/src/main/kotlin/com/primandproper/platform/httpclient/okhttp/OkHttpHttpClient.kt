package com.primandproper.platform.httpclient.okhttp

import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpClientConfig
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.httpclient.executeWithRetries
import com.primandproper.platform.httpclient.recordHttpRequest
import com.primandproper.platform.httpclient.recordHttpResponse
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The OkHttp-backed [HttpClient]: the client-side / Android-friendly backend, the analog of the
 * OkHttp transport platform-go reaches for. Requests are dispatched with `enqueue`, so [execute]
 * suspends without pinning a thread and honours coroutine cancellation.
 *
 * When [HttpClientConfig.enableTracing] is set, calls flow through [OkHttpTelemetry] — the port of
 * Go's `otelhttp.NewTransport`. The instrumentation parents its request span to the OpenTelemetry
 * `Context` current at the point `newCall` is invoked, which (inside an observability `span { }`) is
 * the enclosing operation span — so HTTP spans nest correctly with **no explicit context threading**.
 * With tracing off, or a noop [OpenTelemetry], the request still runs; it simply produces no span.
 *
 * On top of the instrumentation, request/response metadata is attached to the active span via
 * [recordHttpRequest]/[recordHttpResponse], which redact sensitive headers first.
 */
public class OkHttpHttpClient internal constructor(
    private val callFactory: Call.Factory,
    private val underlying: OkHttpClient,
    private val config: HttpClientConfig,
) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = config.executeWithRetries(request) { executeOnce(request) }

    private suspend fun executeOnce(request: HttpRequest): HttpResponse {
        // Captured on the coroutine thread, where the observability span is the current OTel context.
        if (config.enableTracing) Span.current().recordHttpRequest(request)

        val response =
            suspendCancellableCoroutine { continuation ->
                val call = callFactory.newCall(request.toOkHttpRequest())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            continuation.resumeWithException(e)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            val mapped = response.use { it.toHttpResponse() }
                            continuation.resume(mapped)
                        }
                    },
                )
            }

        if (config.enableTracing) Span.current().recordHttpResponse(response)
        return response
    }

    override fun close() {
        underlying.dispatcher.executorService.shutdown()
        underlying.connectionPool.evictAll()
    }

    public companion object {
        private const val KEEP_ALIVE_MINUTES = 5L

        /**
         * Builds an OkHttp-backed client from [config]. Pass the [OpenTelemetry] that
         * `:observability-otel` built (its `OtelTracerProvider` wraps an `OpenTelemetrySdk`, which is
         * an `OpenTelemetry`) to light up tracing; the default [OpenTelemetry.noop] degrades cleanly
         * to untraced calls.
         */
        public fun create(
            config: HttpClientConfig = HttpClientConfig(),
            openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
        ): OkHttpHttpClient {
            config.ensureDefaults()
            config.validate()

            // OkHttp pools per-address; map Go's per-host ceiling onto the pool's idle bound. There's
            // no distinct total-vs-per-host knob, so maxIdleConns is not separately honoured here.
            val client =
                OkHttpClient.Builder()
                    .callTimeout(config.timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .connectionPool(ConnectionPool(config.maxIdleConnsPerHost, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
                    .build()

            val callFactory: Call.Factory =
                if (config.enableTracing) {
                    OkHttpTelemetry.builder(openTelemetry).build().newCallFactory(client)
                } else {
                    client
                }

            return OkHttpHttpClient(callFactory, client, config)
        }
    }
}
