package com.primandproper.platform.routing

/**
 * The HTTP verbs the router can register and dispatch. A closed enum rather than a free-form method
 * string (Go's `r.Method`): a typo can't reach [Router.addRoute] or a [Route], and the per-verb
 * helpers ([Router.get], [Router.post], …) map one-to-one onto these.
 *
 * This is deliberately distinct from `:httpclient-api`'s `HttpMethod`: that enum models the verbs an
 * HTTP *client* sends (no CONNECT/TRACE), whereas a server router must also name CONNECT and TRACE.
 * Duplicating a small enum keeps the server routing layer from depending on the client API.
 */
public enum class HttpMethod {
    CONNECT,
    DELETE,
    GET,
    HEAD,
    OPTIONS,
    PATCH,
    POST,
    PUT,
    TRACE,
    ;

    public companion object {
        /**
         * The [HttpMethod] whose name equals [value] ignoring case (e.g. `"get"` → [GET]), or `null`
         * for an unrecognized verb. The parse edge where a wire string enters the typed world.
         */
        public fun fromWire(value: String): HttpMethod? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
