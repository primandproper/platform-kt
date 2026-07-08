package com.primandproper.platform.httpclient

/**
 * An outbound HTTP request. Deliberately transport-agnostic — the OkHttp and Ktor backends translate
 * it into their own request types — so a caller (and a unit test) can build one without any wire
 * library on the classpath.
 *
 * [body] is raw bytes; higher-level packages (`llm`, `embeddings`, `uploads`) serialize into it. It
 * is a plain class rather than a `data class` because a `ByteArray` has identity equality, which
 * would make generated `equals`/`hashCode` misleading.
 */
public class HttpRequest(
    public val method: HttpMethod,
    public val url: String,
    public val headers: HttpHeaders = HttpHeaders.EMPTY,
    public val body: ByteArray? = null,
) {
    public class Builder {
        public var method: HttpMethod = HttpMethod.GET
        public var url: String = ""
        public var body: ByteArray? = null
        private val headers = HttpHeaders.Builder()

        public fun header(
            name: String,
            value: String,
        ): Builder {
            headers.add(name, value)
            return this
        }

        public fun body(bytes: ByteArray?): Builder {
            body = bytes
            return this
        }

        public fun body(text: String): Builder {
            body = text.toByteArray(Charsets.UTF_8)
            return this
        }

        public fun build(): HttpRequest = HttpRequest(method, url, headers.build(), body)
    }

    public companion object {
        public fun get(url: String): HttpRequest = HttpRequest(HttpMethod.GET, url)

        public fun post(
            url: String,
            body: ByteArray? = null,
        ): HttpRequest = HttpRequest(HttpMethod.POST, url, body = body)

        public inline fun build(block: Builder.() -> Unit): HttpRequest = Builder().apply(block).build()
    }
}
