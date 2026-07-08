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
        val pm = provideCapitalismImplementation(CapitalismConfig(enabled = false))
        assertTrue(pm is NoopPaymentManager)
    }

    @Test
    fun `stripe provider returns a stripe manager`() {
        val pm =
            provideCapitalismImplementation(
                CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE.value),
                stripeConfig = StripeConfig(webhookSecret = "whsec"),
            )
        assertTrue(pm is StripePaymentManager)
    }

    @Test
    fun `stripe provider without a stripe config throws`() {
        assertFailsWith<IllegalArgumentException> {
            provideCapitalismImplementation(CapitalismConfig(enabled = true, provider = PaymentProvider.STRIPE.value))
        }
    }

    @Test
    fun `unknown provider throws`() {
        assertFailsWith<PlatformException> {
            provideCapitalismImplementation(CapitalismConfig(enabled = true, provider = "paypal"))
        }
    }
}
