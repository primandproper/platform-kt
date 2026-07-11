package com.primandproper.platform.httpclient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HttpClientConfigTest {
    @Test
    fun `omitted values resolve to their defaults`() {
        val cfg = HttpClientConfig()

        assertEquals(DEFAULT_TIMEOUT, cfg.timeout())
        assertEquals(DEFAULT_TIMEOUT, cfg.connectTimeout())
        assertEquals(DEFAULT_MAX_IDLE_CONNS, cfg.maxIdleConns)
        assertEquals(DEFAULT_MAX_IDLE_CONNS_PER_HOST, cfg.maxIdleConnsPerHost)
    }

    @Test
    fun `explicit values are preserved`() {
        val cfg =
            HttpClientConfig(
                timeout = 5.seconds,
                maxIdleConns = 50,
                maxIdleConnsPerHost = 25,
            )

        assertEquals(5.seconds, cfg.timeout())
        assertEquals(50, cfg.maxIdleConns)
        assertEquals(25, cfg.maxIdleConnsPerHost)
    }

    @Test
    fun `construction accepts a sane config`() {
        HttpClientConfig(
            timeout = 1.seconds,
            connectTimeout = 1.seconds,
            maxIdleConns = 10,
            maxIdleConnsPerHost = 5,
        )
    }

    @Test
    fun `construction rejects a sub-millisecond timeout`() {
        assertFailsWith<IllegalArgumentException> {
            HttpClientConfig(timeout = Duration.ZERO, connectTimeout = 1.seconds)
        }
    }

    @Test
    fun `construction rejects zero maxIdleConns`() {
        assertFailsWith<IllegalArgumentException> {
            HttpClientConfig(timeout = 1.seconds, connectTimeout = 1.seconds, maxIdleConns = 0)
        }
    }

    @Test
    fun `construction rejects zero maxIdleConnsPerHost`() {
        assertFailsWith<IllegalArgumentException> {
            HttpClientConfig(timeout = 1.seconds, connectTimeout = 1.seconds, maxIdleConnsPerHost = 0)
        }
    }
}
