package com.primandproper.platform.capitalism

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `capitalism/config.TestConfig_ValidateWithContext`. */
class CapitalismConfigTest {
    @Test
    fun `enabled stripe config validates`() {
        val cfg = CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE.value)
        assertNull(cfg.validate())
        assertTrue(cfg.isValid)
    }

    @Test
    fun `disabled config validates regardless of provider`() {
        assertNull(CapitalismConfig(enabled = false).validate())
        assertNull(CapitalismConfig(enabled = false, provider = "nonsense").validate())
    }

    @Test
    fun `enabled config with unknown provider fails`() {
        val cfg = CapitalismConfig(enabled = true, provider = "unknown")
        assertNotNull(cfg.validate())
        assertFalse(cfg.isValid)
    }

    @Test
    fun `provider resolves case-insensitively and trims`() {
        assertEquals(PaymentProvider.STRIPE, PaymentProvider.fromValue("  Stripe "))
        assertNull(PaymentProvider.fromValue("paypal"))
    }
}
