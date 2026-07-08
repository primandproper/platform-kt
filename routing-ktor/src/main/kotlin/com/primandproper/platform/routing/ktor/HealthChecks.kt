package com.primandproper.platform.routing.ktor

/**
 * Request paths that should not be traced or logged — load-balancer and Kubernetes probes. Direct
 * port of platform-go's `routing/chi.healthCheckPaths`.
 */
internal val healthCheckPaths: Set<String> = setOf("/_ops_/live", "/_ops_/ready")

/** Reports whether [path] is a health-check path. Port of `routing/chi.isHealthCheck`. */
internal fun isHealthCheck(path: String): Boolean = path in healthCheckPaths
