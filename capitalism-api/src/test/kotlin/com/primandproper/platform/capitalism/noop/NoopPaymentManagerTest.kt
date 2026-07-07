package com.primandproper.platform.capitalism.noop

import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.SubscriptionCreationInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors platform-go's `capitalism/noop.TestPaymentManager_*`. */
class NoopPaymentManagerTest {
    private val manager = NoopPaymentManager()

    @Test
    fun `handleEventWebhook accepts and drops`() =
        runTest {
            manager.handleEventWebhook("{}".toByteArray(), signatureHeader = "sig")
        }

    @Test
    fun `creates return empty results`() =
        runTest {
            assertEquals("", manager.createCustomer(CustomerCreationInput(email = "x@y.z")))
            assertEquals("", manager.createSubscription(SubscriptionCreationInput(customerID = "c", priceID = "p")))
            val intent = manager.createPaymentIntent(PaymentIntentCreationInput(amount = 1, currency = "usd"))
            assertEquals("", intent.id)
            assertEquals("", intent.clientSecret)
        }
}
