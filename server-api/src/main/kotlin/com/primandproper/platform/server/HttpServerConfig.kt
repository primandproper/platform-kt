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
 * `server/http.Config`. Immutable: an unset timeout is a `null` sentinel resolved on read by
 * [readTimeout]/[writeTimeout]/[idleTimeout] exactly as `provideStdLibHTTPServer`'s `if <= 0` guards
 * do, and the config is validated at construction in [init] rather than in a separate phase.
 *
 * @param port the listen port. Go models this as `uint16`; represented here as an [Int] validated to
 *   `1..65535` — the range a `uint16` port occupies — since Ktor's engine takes an [Int] port.
 * @param startupDeadline bounds how long binding the listener may take. Required, mirroring Go's
 *   `validation.Required` (Ktor cannot bound the bind itself yet — see `TODO(startup-deadline)` in
 *   `:server-ktor`).
 * @param readTimeout / writeTimeout / idleTimeout server socket timeouts; `null` (the default) means
 *   "apply the corresponding `DEFAULT_*_TIMEOUT`", resolved by the like-named getter methods.
 * @param sslCertificateFile / sslCertificateKeyFile PEM paths enabling TLS; `null` means "not set"
 *   (see `TODO(tls)` in the Ktor backend — PEM-to-keystore conversion is required there).
 * @param debug enables debug behavior, mirroring Go's `Debug`.
 */
public data class HttpServerConfig(
    val port: Int,
    val startupDeadline: Duration,
    val readTimeout: Duration? = null,
    val writeTimeout: Duration? = null,
    val idleTimeout: Duration? = null,
    val sslCertificateFile: String? = null,
    val sslCertificateKeyFile: String? = null,
    val debug: Boolean = false,
) {
    init {
        require(port in 1..65_535) { "server: port must be in 1..65535, was $port" }
        require(startupDeadline > Duration.ZERO) { "server: startupDeadline is required" }
    }

    /** True when both TLS PEM paths are set, mirroring Go's `SSLCertificateFile != "" && ...KeyFile != ""`. */
    public val tlsEnabled: Boolean
        get() = !sslCertificateFile.isNullOrEmpty() && !sslCertificateKeyFile.isNullOrEmpty()

    /** The read timeout, or [DEFAULT_READ_TIMEOUT] when unset. Mirrors `provideStdLibHTTPServer`'s guard. */
    public fun readTimeout(): Duration = readTimeout ?: DEFAULT_READ_TIMEOUT

    /** The write timeout, or [DEFAULT_WRITE_TIMEOUT] when unset. */
    public fun writeTimeout(): Duration = writeTimeout ?: DEFAULT_WRITE_TIMEOUT

    /** The idle timeout, or [DEFAULT_IDLE_TIMEOUT] when unset. */
    public fun idleTimeout(): Duration = idleTimeout ?: DEFAULT_IDLE_TIMEOUT
}
