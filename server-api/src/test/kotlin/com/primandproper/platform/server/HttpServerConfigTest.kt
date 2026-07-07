package com.primandproper.platform.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Covers config validation and defaulting from platform-go's `server/http/config_test.go`. */
class HttpServerConfigTest {
    @Test
    fun `port is required to be in range`() {
        assertFailsWith<IllegalArgumentException> {
            HttpServerConfig(port = 0, startupDeadline = 1.seconds).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            HttpServerConfig(port = 70_000, startupDeadline = 1.seconds).validate()
        }
    }

    @Test
    fun `startup deadline is required`() {
        assertFailsWith<IllegalArgumentException> {
            HttpServerConfig(port = 8080, startupDeadline = Duration.ZERO).validate()
        }
    }

    @Test
    fun `a fully-specified config validates`() {
        HttpServerConfig(port = 8080, startupDeadline = 5.seconds).validate()
    }

    @Test
    fun `ensureDefaults fills zero timeouts`() {
        val cfg = HttpServerConfig(port = 8080, startupDeadline = 5.seconds)
        cfg.ensureDefaults()
        assertEquals(DEFAULT_READ_TIMEOUT, cfg.readTimeout)
        assertEquals(DEFAULT_WRITE_TIMEOUT, cfg.writeTimeout)
        assertEquals(DEFAULT_IDLE_TIMEOUT, cfg.idleTimeout)
    }

    @Test
    fun `write timeout default exceeds max timeout`() {
        assertTrue(DEFAULT_WRITE_TIMEOUT > MAX_TIMEOUT)
    }

    @Test
    fun `ensureDefaults preserves explicit timeouts`() {
        val cfg = HttpServerConfig(port = 8080, startupDeadline = 5.seconds, readTimeout = 3.seconds)
        cfg.ensureDefaults()
        assertEquals(3.seconds, cfg.readTimeout)
    }

    @Test
    fun `tlsEnabled reflects both PEM paths being set`() {
        assertFalse(HttpServerConfig(port = 8080, startupDeadline = 5.seconds, sslCertificateFile = "cert").tlsEnabled)
        assertTrue(
            HttpServerConfig(
                port = 8080,
                startupDeadline = 5.seconds,
                sslCertificateFile = "cert",
                sslCertificateKeyFile = "key",
            ).tlsEnabled,
        )
    }
}
