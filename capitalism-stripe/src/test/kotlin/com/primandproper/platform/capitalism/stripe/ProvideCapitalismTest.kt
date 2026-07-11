package com.primandproper.platform.capitalism.stripe

import com.primandproper.platform.capitalism.CapitalismConfig
import com.primandproper.platform.capitalism.PaymentProvider
import com.primandproper.platform.capitalism.noop.NoopPaymentManager
import com.primandproper.platform.errors.PlatformException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Mirrors platform-go's `capitalism/config.TestProvideCapitalismImplementation`. */
class ProvideCapitalismTest {
    @Test
    fun `disabled config returns a noop manager`() {
        val pm = PaymentManager(CapitalismConfig(enabled = false))
        assertTrue(pm is NoopPaymentManager)
    }

    @Test
    fun `stripe provider returns a stripe manager`() {
        val pm =
            PaymentManager(
                CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE),
                stripeConfig = StripeConfig(webhookSecret = "whsec"),
            )
        assertTrue(pm is StripePaymentManager)
    }

    @Test
    fun `stripe provider without a stripe config throws`() {
        assertFailsWith<IllegalArgumentException> {
            PaymentManager(CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE))
        }
    }

    @Test
    fun `an enabled config with no provider throws`() {
        assertFailsWith<PlatformException> {
            PaymentManager(CapitalismConfig(enabled = true, provider = null))
        }
    }
}
