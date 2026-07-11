package com.primandproper.platform.capitalism.stripe

import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Mirrors platform-go's `capitalism/stripe.TestStripeConfig_ValidateWithContext`. */
class StripeConfigTest {
    @Test
    fun `config with a webhook secret validates`() {
        StripeConfig(webhookSecret = "whsec_123").validate()
    }

    @Test
    fun `config without a webhook secret fails`() {
        assertFailsWith<InvalidStripeConfigException> {
            StripeConfig(webhookSecret = "").validate()
        }
    }
}
