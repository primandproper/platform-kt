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
        if (config.enableTracing == true) Span.current().recordHttpRequest(request)

        val hasBody = request.body != null
        val ktorResponse =
            client.request(request.url) {
                method = request.method.toKtorMethod()
                request.headers.forEach { name, values ->
                    // Skip the headers Ktor derives itself from the body/connection. Forwarding them
                    // verbatim — which OkHttp tolerates — otherwise trips Ktor's validateHeaders
                    // (Transfer-Encoding/Upgrade are rejected outright) or fights the value Ktor
                    // computes (Content-Length); a Content-Type with no body is likewise meaningless.
                    // A Content-Type *with* a body is forwarded: Ktor's default transform reads it
                    // back onto the outgoing content, so both backends end up sending the same type.
                    if (isKtorManagedHeader(name, hasBody)) return@forEach
                    values.forEach { headers.append(name, it) }
                }
                request.body?.let { setBody(it) }
            }

        val response = ktorResponse.toHttpResponse()
        if (config.enableTracing == true) Span.current().recordHttpResponse(response)
        return response
    }

    override suspend fun close() {
        client.close()
    }

    /**
     * Whether [name] names a header Ktor manages itself and so must not be forwarded from the caller's
     * request. [KTOR_UNSAFE_HEADERS] (Transfer-Encoding/Upgrade) would make Ktor throw
     * `UnsafeHeaderException`, Content-Length would fight the length Ktor computes from the body, and a
     * Content-Type is only meaningful when there is a body to type — Ktor attaches it to the content.
     */
    private fun isKtorManagedHeader(
        name: String,
        hasBody: Boolean,
    ): Boolean {
        val lower = name.lowercase()
        return lower in KTOR_UNSAFE_HEADERS || (lower == "content-type" && !hasBody)
    }

    public companion object {
        /**
         * Headers Ktor derives from the body/connection and refuses to have set by hand. Content-Length
         * is added when a body is forwarded; Transfer-Encoding and Upgrade are in Ktor's
         * `HttpHeaders.UnsafeHeadersList`, so forwarding them throws `UnsafeHeaderException`. Kept
         * lowercase to match [com.primandproper.platform.httpclient.HttpHeaders]' normalised names.
         */
        private val KTOR_UNSAFE_HEADERS = setOf("content-length", "transfer-encoding", "upgrade")

        /**
         * Builds a Ktor-backed client from [config], over [engine] (CIO by default — pure JVM). Pass
         * the platform's [OpenTelemetry] to enable tracing — the `:observability-otel`
         * `OtelTracerProvider` exposes it via its `openTelemetry` accessor. The default
         * [OpenTelemetry.noop] degrades cleanly to untraced calls. When [HttpClientConfig.enableTracing]
         * is left null (the default), tracing turns on automatically for a non-noop [openTelemetry];
         * set it explicitly to override.
         */
        public fun create(
            config: HttpClientConfig = HttpClientConfig(),
            openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
            engine: HttpClientEngineFactory<*> = CIO,
        ): KtorHttpClient {
            // [config] is already defaulted (via its null sentinels) and validated at construction.
            // Auto-enable tracing when a real SDK is supplied, unless the caller pinned enableTracing;
            // the resolved value is pinned onto the config the client instance carries.
            val resolved = config.copy(enableTracing = config.enableTracing ?: (openTelemetry !== OpenTelemetry.noop()))

            val client =
                KtorClient(engine) {
                    expectSuccess = false
                    install(HttpTimeout) {
                        requestTimeoutMillis = resolved.timeout().inWholeMilliseconds
                        connectTimeoutMillis = resolved.connectTimeout().inWholeMilliseconds
                    }
                    if (resolved.enableTracing == true) {
                        install(KtorClientTelemetry) {
                            setOpenTelemetry(openTelemetry)
                        }
                    }
                }

            return KtorHttpClient(client, resolved)
        }
    }
}
