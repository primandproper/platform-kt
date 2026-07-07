package com.primandproper.platform.capitalism.mock

import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntent
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.SubscriptionCreationInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the hand-written [PaymentManagerMock], the analog of moq's generated `PaymentManagerMock`. */
class PaymentManagerMockTest {
    @Test
    fun `delegates to funcs and records calls`() =
        runTest {
            val mock =
                PaymentManagerMock(
                    handleEventWebhookFunc = { _, _ -> },
                    createCustomerFunc = { input -> "cus_${input.email}" },
                    createPaymentIntentFunc = { input -> PaymentIntent(id = "pi", clientSecret = "cs_${input.amount}") },
                    createSubscriptionFunc = { input -> "sub_${input.priceID}" },
                )

            mock.handleEventWebhook("{}".toByteArray(), "sig")
            assertEquals("cus_a@b.c", mock.createCustomer(CustomerCreationInput(email = "a@b.c")))
            assertEquals("cs_1000", mock.createPaymentIntent(PaymentIntentCreationInput(amount = 1000, currency = "usd")).clientSecret)
            assertEquals("sub_price_1", mock.createSubscription(SubscriptionCreationInput(customerID = "c", priceID = "price_1")))

            assertEquals(1, mock.handleEventWebhookCalls.size)
            assertEquals("sig", mock.handleEventWebhookCalls.single().second)
            assertEquals("a@b.c", mock.createCustomerCalls.single().email)
            assertEquals(1000, mock.createPaymentIntentCalls.single().amount)
            assertEquals("price_1", mock.createSubscriptionCalls.single().priceID)
        }

    @Test
    fun `unmocked method throws`() =
        runTest {
            val mock = PaymentManagerMock()
            assertFailsWith<IllegalStateException> { mock.createCustomer(CustomerCreationInput()) }
        }
}
