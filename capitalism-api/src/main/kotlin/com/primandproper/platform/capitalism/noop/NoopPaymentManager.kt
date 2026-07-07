package com.primandproper.platform.capitalism.noop

import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntent
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.PaymentManager
import com.primandproper.platform.capitalism.SubscriptionCreationInput

/**
 * A no-op [PaymentManager]: webhooks are accepted and dropped, and every create returns an empty
 * result without contacting a provider. Port of platform-go's `capitalism/noop.paymentManager` — the
 * safe default the config factory returns when payments are disabled, and a stand-in for tests that
 * don't care about real payment behavior.
 */
public class NoopPaymentManager : PaymentManager {
    override suspend fun handleEventWebhook(
        payload: ByteArray,
        signatureHeader: String,
    ) {
    }

    override suspend fun createCustomer(input: CustomerCreationInput): String = ""

    override suspend fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent = PaymentIntent(id = "", clientSecret = "")

    override suspend fun createSubscription(input: SubscriptionCreationInput): String = ""
}
