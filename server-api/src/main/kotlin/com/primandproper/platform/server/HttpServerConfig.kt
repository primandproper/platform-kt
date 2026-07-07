package com.primandproper.platform.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Router request timeout mirror. Port of `server/http.maxTimeout` (= `routing/chi.maxTimeout`). */
public val MAX_TIMEOUT: Duration = 120.seconds

/** Default read timeout. Port of `server/http.readTimeout`. */
public val DEFAULT_READ_TIMEOUT: Duration = 5.seconds

/**
 * Default write timeout. Port of `server/http.writeTimeout` — deliberately larger than [MAX_TIMEOUT]
 * so the router's 120s request timeout is reachable and slow responses aren't severed mid-write.
 */
public val DEFAULT_WRITE_TIMEOUT: Duration = MAX_TIMEOUT + 30.seconds

/** Default idle timeout. Port of `server/http.idleTimeout`. */
public val DEFAULT_IDLE_TIMEOUT: Duration = MAX_TIMEOUT

/**
 * The settings pertinent to the HTTP serving portion of a service. Direct port of platform-go's
 * `server/http.Config`, with [ensureDefaults] filling the zero-valued timeouts exactly as
 * `provideStdLibHTTPServer`'s `if <= 0` guards do.
 *
 * @param port the listen port. Go models this as `uint16`; represented here as an [Int] validated to
 *   `1..65535` — the range a `uint16` port occupies — since Ktor's engine takes an `Int` port.
 * @param startupDeadline bounds how long binding the listener may take. Required, mirroring Go's
 *   `validation.Required` (Ktor cannot bound the bind itself yet — see `TODO(startup-deadline)` in
 *   `:server-ktor`).
 * @param readTimeout / writeTimeout / idleTimeout server socket timeouts; zero means "apply the
 *   default in [ensureDefaults]".
 * @param sslCertificateFile / sslCertificateKeyFile PEM paths enabling TLS (see `TODO(tls)` in the
 *   Ktor backend — PEM-to-keystore conversion is required there).
 * @param debug enables debug behavior, mirroring Go's `Debug`.
 */
public data class HttpServerConfig(
    val port: Int,
    val startupDeadline: Duration,
    var readTimeout: Duration = Duration.ZERO,
    var writeTimeout: Duration = Duration.ZERO,
    var idleTimeout: Duration = Duration.ZERO,
    val sslCertificateFile: String = "",
    val sslCertificateKeyFile: String = "",
    val debug: Boolean = false,
) {
    /** True when both TLS PEM paths are set, mirroring Go's `SSLCertificateFile != "" && ...KeyFile != ""`. */
    public val tlsEnabled: Boolean
        get() = sslCertificateFile.isNotEmpty() && sslCertificateKeyFile.isNotEmpty()

    /** Fills zero-valued timeouts with their defaults. Mirrors `provideStdLibHTTPServer`'s guards. */
    public fun ensureDefaults() {
        if (readTimeout <= Duration.ZERO) readTimeout = DEFAULT_READ_TIMEOUT
        if (writeTimeout <= Duration.ZERO) writeTimeout = DEFAULT_WRITE_TIMEOUT
        if (idleTimeout <= Duration.ZERO) idleTimeout = DEFAULT_IDLE_TIMEOUT
    }

    /** Validates the config, throwing [IllegalArgumentException] on the first problem. Mirrors `ValidateWithContext`. */
    public fun validate() {
        require(port in 1..65_535) { "server: port must be in 1..65535, was $port" }
        require(startupDeadline > Duration.ZERO) { "server: startupDeadline is required" }
    }
}
