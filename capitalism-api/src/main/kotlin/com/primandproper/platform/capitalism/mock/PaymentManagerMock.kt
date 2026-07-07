package com.primandproper.platform.capitalism.mock

import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntent
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.PaymentManager
import com.primandproper.platform.capitalism.SubscriptionCreationInput

/**
 * A configurable [PaymentManager] test double, mirroring platform-go's moq-generated
 * `capitalismmock.PaymentManagerMock`. Each method delegates to a settable `...Func`; calling a method
 * whose `Func` was left `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's arguments are recorded in the
 * matching `...Calls` list, standing in for moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = PaymentManagerMock(createCustomerFunc = { input -> "cus_${input.email}" })
 * ```
 */
public class PaymentManagerMock(
    public var handleEventWebhookFunc: (suspend (ByteArray, String) -> Unit)? = null,
    public var createCustomerFunc: (suspend (CustomerCreationInput) -> String)? = null,
    public var createPaymentIntentFunc: (suspend (PaymentIntentCreationInput) -> PaymentIntent)? = null,
    public var createSubscriptionFunc: (suspend (SubscriptionCreationInput) -> String)? = null,
) : PaymentManager {
    public val handleEventWebhookCalls: MutableList<Pair<ByteArray, String>> = mutableListOf()
    public val createCustomerCalls: MutableList<CustomerCreationInput> = mutableListOf()
    public val createPaymentIntentCalls: MutableList<PaymentIntentCreationInput> = mutableListOf()
    public val createSubscriptionCalls: MutableList<SubscriptionCreationInput> = mutableListOf()

    override suspend fun handleEventWebhook(
        payload: ByteArray,
        signatureHeader: String,
    ) {
        handleEventWebhookCalls += payload to signatureHeader
        requireFunc(handleEventWebhookFunc, "handleEventWebhookFunc").invoke(payload, signatureHeader)
    }

    override suspend fun createCustomer(input: CustomerCreationInput): String {
        createCustomerCalls += input
        return requireFunc(createCustomerFunc, "createCustomerFunc").invoke(input)
    }

    override suspend fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent {
        createPaymentIntentCalls += input
        return requireFunc(createPaymentIntentFunc, "createPaymentIntentFunc").invoke(input)
    }

    override suspend fun createSubscription(input: SubscriptionCreationInput): String {
        createSubscriptionCalls += input
        return requireFunc(createSubscriptionFunc, "createSubscriptionFunc").invoke(input)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("PaymentManagerMock.$name: method is null but was just called")
