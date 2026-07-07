package com.primandproper.platform.routing.ktor

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import java.util.UUID

/** The call attribute the request-ID middleware stores the request ID under. */
internal val RequestIdKey: AttributeKey<String> = AttributeKey("com.primandproper.platform.routing.RequestId")

/** Header a client (or upstream proxy) may use to supply a request ID, honored when present. */
internal const val REQUEST_ID_HEADER: String = "X-Request-ID"

/**
 * Returns the request ID assigned to [call] by [installRequestId], or `""` if none. Analog of Go's
 * `chi.RequestIDFunc`, which reads chi's `RequestIDKey` from the request context.
 */
public fun requestIdOf(call: ApplicationCall): String = call.attributes.getOrNull(RequestIdKey) ?: ""

/** Generates a fresh request ID. Chi builds a `prefix-counter`; a UUID is the portable equivalent. */
internal fun generateRequestId(): String = UUID.randomUUID().toString()
