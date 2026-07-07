package com.primandproper.platform.httpclient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class HttpClientConfigTest {
    @Test
    fun `ensureDefaults sets defaults for zero values`() {
        val cfg = HttpClientConfig()
        cfg.ensureDefaults()

        assertEquals(DEFAULT_TIMEOUT, cfg.timeout)
        assertEquals(DEFAULT_TIMEOUT, cfg.connectTimeout)
        assertEquals(DEFAULT_MAX_IDLE_CONNS, cfg.maxIdleConns)
        assertEquals(DEFAULT_MAX_IDLE_CONNS_PER_HOST, cfg.maxIdleConnsPerHost)
    }

    @Test
    fun `ensureDefaults preserves non-zero values`() {
        val cfg =
            HttpClientConfig {
                timeout = 5.seconds
                maxIdleConns = 50
                maxIdleConnsPerHost = 25
            }
        cfg.ensureDefaults()

        assertEquals(5.seconds, cfg.timeout)
        assertEquals(50, cfg.maxIdleConns)
        assertEquals(25, cfg.maxIdleConnsPerHost)
    }

    @Test
    fun `validate accepts a sane config`() {
        val cfg =
            HttpClientConfig {
                timeout = 1.seconds
                connectTimeout = 1.seconds
                maxIdleConns = 10
                maxIdleConnsPerHost = 5
            }
        cfg.validate()
    }

    @Test
    fun `validate rejects zero timeout`() {
        val cfg =
            HttpClientConfig {
                connectTimeout = 1.seconds
                maxIdleConns = 10
                maxIdleConnsPerHost = 5
            }
        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }

    @Test
    fun `validate rejects zero maxIdleConns`() {
        val cfg =
            HttpClientConfig {
                timeout = 1.seconds
                connectTimeout = 1.seconds
                maxIdleConnsPerHost = 5
            }
        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }

    @Test
    fun `validate rejects zero maxIdleConnsPerHost`() {
        val cfg =
            HttpClientConfig {
                timeout = 1.seconds
                connectTimeout = 1.seconds
                maxIdleConns = 10
            }
        assertFailsWith<IllegalArgumentException> { cfg.validate() }
    }
}
