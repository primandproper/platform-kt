package com.primandproper.platform.httpclient

import java.nio.charset.Charset

/**
 * A fully-buffered HTTP response. Backends read the body into memory before returning, so the
 * response outlives the underlying connection — the ergonomic choice for the request/response call
 * sites this client serves (streaming lands with the `eventstream`/`uploads` ports, not here).
 */
public class HttpResponse(
    public val statusCode: Int,
    public val headers: HttpHeaders = HttpHeaders.EMPTY,
    public val body: ByteArray = EMPTY_BODY,
) {
    /** True for a 2xx status. */
    public val isSuccessful: Boolean get() = statusCode in 200..299

    public fun bodyAsText(charset: Charset = Charsets.UTF_8): String = String(body, charset)

    private companion object {
        private val EMPTY_BODY = ByteArray(0)
    }
}
