package com.primandproper.platform.capitalism

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Mirrors platform-go's `capitalism/config.TestConfig_ValidateWithContext`. */
class CapitalismConfigTest {
    @Test
    fun `enabled stripe config validates`() {
        CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE).validate()
    }

    @Test
    fun `disabled config validates regardless of provider`() {
        CapitalismConfig(enabled = false).validate()
        CapitalismConfig(enabled = false, provider = null).validate()
    }

    @Test
    fun `enabled config without a provider fails`() {
        assertFailsWith<InvalidPaymentProviderException> {
            CapitalismConfig(enabled = true, provider = null).validate()
        }
    }

    @Test
    fun `provider resolves case-insensitively and trims`() {
        assertEquals(PaymentProvider.STRIPE, PaymentProvider.fromValue("  Stripe "))
        assertNull(PaymentProvider.fromValue("paypal"))
    }
}
