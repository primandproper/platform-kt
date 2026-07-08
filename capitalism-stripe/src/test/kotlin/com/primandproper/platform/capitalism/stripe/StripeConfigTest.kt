package com.primandproper.platform.capitalism.stripe

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `capitalism/stripe.TestStripeConfig_ValidateWithContext`. */
class StripeConfigTest {
    @Test
    fun `config with a webhook secret validates`() {
        val cfg = StripeConfig(webhookSecret = "whsec_123")
        assertNull(cfg.validate())
        assertTrue(cfg.isValid)
    }

    @Test
    fun `config without a webhook secret fails`() {
        val cfg = StripeConfig(webhookSecret = "")
        assertNotNull(cfg.validate())
        assertFalse(cfg.isValid)
    }
}
