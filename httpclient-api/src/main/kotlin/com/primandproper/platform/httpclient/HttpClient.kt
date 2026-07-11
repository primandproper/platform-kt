package com.primandproper.platform.httpclient

import com.primandproper.platform.observability.SuspendCloseable

/**
 * The traced HTTP client contract — the keystone every networked package (`llm`, `embeddings`,
 * `uploads`, …) calls through. Coroutine-native: [execute] is a `suspend` function, so a call made
 * inside an observability `span { }` inherits that span from the coroutine context and the backend's
 * OpenTelemetry instrumentation parents its request span underneath it. When tracing is absent the
 * call still runs — it just produces no span (see each backend's noop degradation).
 *
 * It is an interface so tests substitute [FakeHttpClient] and DI can swap the OkHttp (client/Android)
 * backend for the Ktor (server) one without touching call sites — the same seam `Observer` gives
 * observability.
 */
public interface HttpClient : SuspendCloseable {
    /** Executes [request] and returns a fully-buffered [HttpResponse]. Suspends until complete. */
    public suspend fun execute(request: HttpRequest): HttpResponse

    /**
     * Releases the backend's connection pool / dispatcher. Idempotent; safe to skip for short-lived
     * clients. `suspend` via [SuspendCloseable] so a backend may drain in-flight work without blocking.
     */
    override suspend fun close() {}
}

/** A client that does nothing and returns an empty `200`. The safe default where a real one is optional. */
public object NoopHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = HttpResponse(statusCode = 200)
}

/**
 * A test double that records every request and returns whatever [handler] produces (a static `200`
 * by default). The `RecordingObserver` analog for HTTP — assert on [requests] to check what a unit
 * sent, without a wire library or a live server.
 */
public class FakeHttpClient(
    private val handler: suspend (HttpRequest) -> HttpResponse = { HttpResponse(statusCode = 200) },
) : HttpClient {
    private val recorded = mutableListOf<HttpRequest>()

    /** Every request passed to [execute], in order. */
    public val requests: List<HttpRequest> get() = recorded

    /** The most recent request, or null if none. */
    public val lastRequest: HttpRequest? get() = recorded.lastOrNull()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        recorded.add(request)
        return handler(request)
    }
}
