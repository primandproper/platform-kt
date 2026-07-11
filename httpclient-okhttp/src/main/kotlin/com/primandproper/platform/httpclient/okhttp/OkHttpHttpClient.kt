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
        if (config.enableTracing == true) Span.current().recordHttpRequest(request)

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
                            // Reading the body can throw (truncated/corrupt body, or `callTimeout` firing
                            // mid-read and cancelling the call). OkHttp has already marked the callback as
                            // signalled by this point, so `onFailure` will never fire — resume with the
                            // failure here so the suspended caller is never left hanging.
                            val mapped =
                                try {
                                    response.use { it.toHttpResponse() }
                                } catch (e: Throwable) {
                                    continuation.resumeWithException(e)
                                    return
                                }
                            continuation.resume(mapped)
                        }
                    },
                )
            }

        if (config.enableTracing == true) Span.current().recordHttpResponse(response)
        return response
    }

    override suspend fun close() {
        underlying.dispatcher.executorService.shutdown()
        underlying.connectionPool.evictAll()
    }

    public companion object {
        private const val KEEP_ALIVE_MINUTES = 5L

        /**
         * Builds an OkHttp-backed client from [config]. Pass the platform's [OpenTelemetry] to light
         * up tracing — the `:observability-otel` `OtelTracerProvider` exposes it via its
         * `openTelemetry` accessor. The default [OpenTelemetry.noop] degrades cleanly to untraced
         * calls. When [HttpClientConfig.enableTracing] is left null (the default), tracing turns on
         * automatically for a non-noop [openTelemetry]; set it explicitly to override.
         */
        public fun create(
            config: HttpClientConfig = HttpClientConfig(),
            openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
        ): OkHttpHttpClient {
            // [config] is already defaulted (via its null sentinels) and validated at construction.
            // Auto-enable tracing when a real SDK is supplied, unless the caller pinned enableTracing;
            // the resolved value is pinned onto the config the client instance carries.
            val resolved = config.copy(enableTracing = config.enableTracing ?: (openTelemetry !== OpenTelemetry.noop()))

            // OkHttp pools per-address; map Go's per-host ceiling onto the pool's idle bound. There's
            // no distinct total-vs-per-host knob, so maxIdleConns is not separately honoured here.
            val client =
                OkHttpClient.Builder()
                    .callTimeout(resolved.timeout().inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .connectTimeout(resolved.connectTimeout().inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .connectionPool(ConnectionPool(resolved.maxIdleConnsPerHost, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
                    .build()

            val callFactory: Call.Factory =
                if (resolved.enableTracing == true) {
                    OkHttpTelemetry.builder(openTelemetry).build().newCallFactory(client)
                } else {
                    client
                }

            return OkHttpHttpClient(callFactory, client, resolved)
        }
    }
}
