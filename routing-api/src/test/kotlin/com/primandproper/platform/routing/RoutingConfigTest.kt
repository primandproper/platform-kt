package com.primandproper.platform.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Covers the provider resolution and settings validation from platform-go's
 * `routing/config/config_test.go` and `routing/chi/config_test.go`.
 */
class RoutingConfigTest {
    @Test
    fun `ktor provider resolves case-insensitively and trimmed`() {
        assertEquals(RoutingProvider.KTOR, RoutingProvider.fromValue("  Ktor "))
    }

    @Test
    fun `unknown provider resolves to null`() {
        assertNull(RoutingProvider.fromValue("chi"))
    }

    @Test
    fun `settings require a service name`() {
        assertFailsWith<IllegalArgumentException> { RouterSettings(serviceName = "  ").validate() }
    }

    @Test
    fun `valid config validates`() {
        RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "api")).validate()
    }
}
