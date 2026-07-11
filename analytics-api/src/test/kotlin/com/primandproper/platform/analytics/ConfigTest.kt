package com.primandproper.platform.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Mirrors the config-shape assertions from platform-go's `analytics/config/config_test.go`. */
class ConfigTest {
    @Test
    fun `valid segment source passes validation`() {
        SourceConfig(provider = AnalyticsProvider.SEGMENT, segment = SegmentConfig(apiToken = "token")).validate()
    }

    @Test
    fun `segment provider without token fails validation`() {
        assertFailsWith<InvalidAnalyticsSourceConfigException> {
            SourceConfig(provider = AnalyticsProvider.SEGMENT).validate()
        }
    }

    @Test
    fun `posthog provider without key fails validation`() {
        assertFailsWith<InvalidAnalyticsSourceConfigException> {
            SourceConfig(provider = AnalyticsProvider.POSTHOG).validate()
        }
    }

    @Test
    fun `fromValue resolves known providers and returns null for unknown or blank`() {
        assertEquals(AnalyticsProvider.SEGMENT, AnalyticsProvider.fromValue("  SEGMENT "))
        assertEquals(AnalyticsProvider.POSTHOG, AnalyticsProvider.fromValue("posthog"))
        assertNull(AnalyticsProvider.fromValue("bogus"))
        assertNull(AnalyticsProvider.fromValue(""))
    }

    @Test
    fun `toMap skips nil sources`() {
        assertTrue(ProxySourcesConfig().toMap().isEmpty())
    }

    @Test
    fun `toMap with only ios`() {
        val ios = SourceConfig(provider = AnalyticsProvider.SEGMENT)
        val m = ProxySourcesConfig(ios = ios).toMap()
        assertEquals(1, m.size)
        assertSame(ios, m["ios"])
    }

    @Test
    fun `toMap with both sources`() {
        val ios = SourceConfig(provider = AnalyticsProvider.SEGMENT)
        val web = SourceConfig(provider = AnalyticsProvider.POSTHOG)
        val m = ProxySourcesConfig(ios = ios, web = web).toMap()
        assertEquals(2, m.size)
        assertSame(ios, m["ios"])
        assertSame(web, m["web"])
    }
}
