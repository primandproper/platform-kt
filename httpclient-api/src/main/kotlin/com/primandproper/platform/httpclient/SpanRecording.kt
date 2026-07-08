package com.primandproper.platform.httpclient

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Span

/**
 * Low-cardinality span naming, the port of platform-go's `tracing.FormatSpan`: numeric path segments
 * (ids) collapse to `<id>` so `/users/42/posts/7` and `/users/9/posts/3` share one span name instead
 * of exploding the trace index. Backends whose instrumentation lets them name the span use this.
 */
public object HttpSpanNames {
    private val ID_SEGMENT = Regex("""/\d+""")

    public fun format(
        method: HttpMethod,
        path: String,
    ): String = "${method.name} ${ID_SEGMENT.replace(path, "/<id>")}"
}

/**
 * Attaches [request] to the active span, **redacting sensitive headers first** via [HeaderRedaction].
 * No-ops on a non-recording span (noop tracer / sampled-out), so callers never branch — the same
 * guard `Operation.setAttributeAny` uses in observability-api. This is the single place request
 * headers touch a span, which is what makes the redaction guarantee enforceable.
 */
public fun Span.recordHttpRequest(request: HttpRequest) {
    if (!isRecording) return
    setAttribute(Keys.REQUEST_METHOD, request.method.name)
    setAttribute(Keys.REQUEST_URI, request.url)
    request.headers.forEach { name, values ->
        setAttribute("http.request.header.$name", HeaderRedaction.redactValues(name, values).joinToString(","))
    }
}

/** Attaches [response]'s status and (redacted) headers to the active span. No-ops when not recording. */
public fun Span.recordHttpResponse(response: HttpResponse) {
    if (!isRecording) return
    setAttribute(Keys.RESPONSE_STATUS, response.statusCode.toLong())
    response.headers.forEach { name, values ->
        setAttribute("http.response.header.$name", HeaderRedaction.redactValues(name, values).joinToString(","))
    }
}
