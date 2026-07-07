package com.primandproper.platform.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Mirrors the config-shape assertions from platform-go's `analytics/config/config_test.go`. */
class ConfigTest {
    @Test
    fun `valid segment source passes validation`() {
        val cfg = SourceConfig(provider = Provider.SEGMENT, segment = SegmentConfig(apiToken = "token"))
        assertNull(cfg.validate())
        assertTrue(cfg.isValid)
    }

    @Test
    fun `segment provider without token fails validation`() {
        val cfg = SourceConfig(provider = Provider.SEGMENT)
        assertTrue(cfg.validate() != null)
    }

    @Test
    fun `posthog provider without key fails validation`() {
        val cfg = SourceConfig(provider = Provider.POSTHOG)
        assertTrue(cfg.validate() != null)
    }

    @Test
    fun `unknown provider fails validation`() {
        assertTrue(SourceConfig(provider = "bogus").validate() != null)
        assertTrue(SourceConfig(provider = "").validate() != null)
    }

    @Test
    fun `toMap skips nil sources`() {
        assertTrue(ProxySourcesConfig().toMap().isEmpty())
    }

    @Test
    fun `toMap with only ios`() {
        val ios = SourceConfig(provider = Provider.SEGMENT)
        val m = ProxySourcesConfig(ios = ios).toMap()
        assertEquals(1, m.size)
        assertSame(ios, m["ios"])
    }

    @Test
    fun `toMap with both sources`() {
        val ios = SourceConfig(provider = Provider.SEGMENT)
        val web = SourceConfig(provider = Provider.POSTHOG)
        val m = ProxySourcesConfig(ios = ios, web = web).toMap()
        assertEquals(2, m.size)
        assertSame(ios, m["ios"])
        assertSame(web, m["web"])
    }
}
