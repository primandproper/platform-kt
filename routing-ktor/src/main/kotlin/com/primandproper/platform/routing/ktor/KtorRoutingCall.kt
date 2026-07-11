package com.primandproper.platform.routing.ktor

import com.primandproper.platform.routing.HttpMethod
import com.primandproper.platform.routing.RoutingCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

/**
 * Adapts a Ktor [ApplicationCall] to the framework-free [RoutingCall] the routing API defines. This
 * is the concrete request/response type Go passes as `(http.ResponseWriter, *http.Request)`; here it
 * is the one place Ktor's call type is bound, keeping `:routing-api` provider-agnostic.
 */
internal class KtorRoutingCall(
    private val call: ApplicationCall,
) : RoutingCall {
    // Ktor only dispatches to this call after matching a registered verb, so the method is always one
    // the platform enum names; fall back to GET defensively for any exotic verb rather than throw.
    override val method: HttpMethod get() = HttpMethod.fromWire(call.request.httpMethod.value) ?: HttpMethod.GET

    override val path: String get() = call.request.path()

    override val requestId: String? get() = call.attributes.getOrNull(RequestIdKey)

    override fun pathParameter(key: String): String? = call.parameters[key]

    override fun header(name: String): String? = call.request.header(name)

    override suspend fun respondText(
        status: Int,
        text: String,
    ) {
        call.respondText(text = text, status = HttpStatusCode.fromValue(status))
    }

    override suspend fun respondBytes(
        status: Int,
        contentType: String,
        bytes: ByteArray,
    ) {
        call.respondBytes(bytes = bytes, contentType = ContentType.parse(contentType), status = HttpStatusCode.fromValue(status))
    }

    override suspend fun respondStatus(status: Int) {
        call.respond(HttpStatusCode.fromValue(status))
    }
}
