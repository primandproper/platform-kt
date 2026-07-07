package com.primandproper.platform.httpclient

/**
 * The HTTP verbs the client models. Kept as a closed enum rather than Go's free-form method string:
 * the backends only need to map these to their own vocabulary, and a typo can't reach the wire.
 */
public enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
}
