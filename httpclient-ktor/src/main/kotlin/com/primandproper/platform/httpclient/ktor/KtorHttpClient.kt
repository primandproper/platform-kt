package com.primandproper.platform.httpclient.ktor

import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpClientConfig
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.httpclient.executeWithRetries
import com.primandproper.platform.httpclient.recordHttpRequest
import com.primandproper.platform.httpclient.recordHttpResponse
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import io.ktor.client.HttpClient as KtorClient

/**
 * The Ktor-backed [HttpClient]: the server-side backend. Ktor's client is natively `suspend`, so
 * [execute] issues the request on the calling coroutine directly — which means the OpenTelemetry Ktor
 * instrumentation ([KtorClientTelemetry]) sees the enclosing observability span as the current OTel
 * Context and parents its request span underneath it, matching the OkHttp backend's tie-in. With
 * tracing off (or a noop [OpenTelemetry]) the request runs untraced.
 *
 * Request/response metadata is attached to the active span through [recordHttpRequest]/
 * [recordHttpResponse], which redact sensitive headers first.
 */
public class KtorHttpClient internal constructor(
    private val client: KtorClient,
    private val config: HttpClientConfig,
) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = config.executeWithRetries(request) { executeOnce(request) }

    private suspend fun executeOnce(request: HttpRequest): HttpResponse {
        if (config.enableTracing) Span.current().recordHttpRequest(request)

        val ktorResponse =
            client.request(request.url) {
                method = request.method.toKtorMethod()
                request.headers.forEach { name, values ->
                    values.forEach { headers.append(name, it) }
                }
                request.body?.let { setBody(it) }
            }

        val response = ktorResponse.toHttpResponse()
        if (config.enableTracing) Span.current().recordHttpResponse(response)
        return response
    }

    override fun close() {
        client.close()
    }

    public companion object {
        /**
         * Builds a Ktor-backed client from [config], over [engine] (CIO by default — pure JVM). Pass
         * the [OpenTelemetry] that `:observability-otel` built to enable tracing; the default
         * [OpenTelemetry.noop] degrades cleanly to untraced calls.
         */
        public fun create(
            config: HttpClientConfig = HttpClientConfig(),
            openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
            engine: HttpClientEngineFactory<*> = CIO,
        ): KtorHttpClient {
            config.ensureDefaults()
            config.validate()

            val client =
                KtorClient(engine) {
                    expectSuccess = false
                    install(HttpTimeout) {
                        requestTimeoutMillis = config.timeout.inWholeMilliseconds
                        connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
                    }
                    if (config.enableTracing) {
                        install(KtorClientTelemetry) {
                            setOpenTelemetry(openTelemetry)
                        }
                    }
                }

            return KtorHttpClient(client, config)
        }
    }
}
