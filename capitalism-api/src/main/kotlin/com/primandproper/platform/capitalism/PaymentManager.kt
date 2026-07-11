package com.primandproper.platform.capitalism

/**
 * Handles payments via third-party providers — the port of platform-go's `capitalism.PaymentManager`.
 *
 * The Go interface threads a `context.Context` through the outbound methods and returns an `error`;
 * the coroutine-native Kotlin analog drops the explicit context (trace context rides the coroutine
 * scope, installed by the backend's Observer span) and signals failure by throwing rather than
 * returning an error value. Every method is therefore `suspend`, so a backend can await its transport
 * without blocking a thread.
 *
 * The inbound webhook path takes the raw request bytes and the provider's signature header rather than
 * a framework `Request`, keeping this contract free of any HTTP-server dependency: Go's
 * `HandleEventWebhook(*http.Request)` reads the body and the `Stripe-Signature` header off the request;
 * a Kotlin caller (a Ktor route, a servlet, an Android callback) reads those two values and hands them
 * across.
 */
public interface PaymentManager {
    /**
     * Verifies and processes an inbound provider webhook (e.g. a Stripe event). [payload] is the raw,
     * unparsed request body — verification is over the exact bytes — and [signatureHeader] is the
     * value of the provider's signature header (for Stripe, `Stripe-Signature`). Throws when the
     * signature fails to verify or the body is unreadable/oversized.
     */
    public suspend fun handleEventWebhook(
        payload: ByteArray,
        signatureHeader: String,
    )

    /** Creates a customer with the provider and returns its provider-assigned ID. */
    public suspend fun createCustomer(input: CustomerCreationInput): String

    /** Creates a payment intent (a single charge in progress) and returns it. */
    public suspend fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent

    /** Subscribes a customer to a price/plan and returns the subscription ID. */
    public suspend fun createSubscription(input: SubscriptionCreationInput): String
}

/**
 * An ISO 4217 currency code (e.g. `usd`, `eur`). A typed wrapper over the 3-letter code so a payment
 * amount cannot be paired with an arbitrary or malformed currency string; the code is validated at
 * construction. Kept lowercase-friendly because that is what the payment providers' wire APIs expect.
 */
@JvmInline
public value class Currency(public val code: String) {
    init {
        require(code.length == 3 && code.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            "currency must be a 3-letter ISO 4217 code, got \"$code\""
        }
    }

    public companion object {
        public val USD: Currency = Currency("usd")
        public val EUR: Currency = Currency("eur")
        public val GBP: Currency = Currency("gbp")
    }
}

/**
 * Describes a customer to create. Port of platform-go's `capitalism.CustomerCreationInput`. All fields
 * are optional except where a provider requires them; [idempotencyKey], when non-null, makes the
 * create safely retryable.
 */
public data class CustomerCreationInput(
    val email: String = "",
    val name: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
)

/**
 * Describes a payment to initiate. Port of platform-go's `capitalism.PaymentIntentCreationInput`.
 * [amount] is in the smallest unit of [currency] (e.g. cents for USD); [idempotencyKey], when
 * non-null, makes the create safely retryable.
 */
public data class PaymentIntentCreationInput(
    val amount: Long,
    val currency: Currency,
    val customerID: String = "",
    val description: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
)

/**
 * The result of creating a payment intent. Port of platform-go's `capitalism.PaymentIntent`.
 * [clientSecret] is handed to a client SDK to complete the payment.
 */
public data class PaymentIntent(
    val id: String,
    val clientSecret: String,
)

/**
 * Describes a subscription to create: a customer subscribed to a single price. Port of platform-go's
 * `capitalism.SubscriptionCreationInput`. [idempotencyKey], when non-null, makes the create safely
 * retryable.
 */
public data class SubscriptionCreationInput(
    val customerID: String,
    val priceID: String,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
)
