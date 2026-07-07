package com.primandproper.platform.capitalism.stripe

import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntent
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.PaymentManager
import com.primandproper.platform.capitalism.SubscriptionCreationInput
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.newError
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.stripe.StripeClient
import com.stripe.exception.EventDataObjectDeserializationException
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.net.RequestOptions
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.SubscriptionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.stripe.model.PaymentIntent as StripePaymentIntent

/** Component name for the Stripe manager's Observer. Mirrors platform-go's `stripe.implementationName`. */
internal const val NAME: String = "stripe_payment_manager"

/** Stripe's event type for a succeeded payment intent, the one type Go enriches observability for. */
private const val EVENT_PAYMENT_INTENT_SUCCEEDED: String = "payment_intent.succeeded"

/**
 * Caps how much of a webhook body is accepted. Go reads the body through `http.MaxBytesReader` so a
 * hostile client can't force an unbounded allocation on this public, unauthenticated endpoint; the
 * Kotlin caller has already read the bytes, so the port enforces the same 64 KiB ceiling on the
 * supplied payload instead. Stripe event payloads are well under this.
 */
private const val MAX_WEBHOOK_BODY_BYTES: Int = 64 shl 10 // 64 KiB

/**
 * Thrown when an outbound operation is attempted without a configured Stripe API key. The webhook path
 * needs only the webhook secret, so the key is optional at construction; outbound operations require
 * it. Port of platform-go's `stripe.ErrAPIKeyNotConfigured`.
 */
public val ErrApiKeyNotConfigured: PlatformException =
    newError("stripe API key not configured; set the API key to use outbound operations")

/**
 * An optional callback invoked with each verified Stripe [Event], letting a consumer act on a webhook
 * (e.g. fulfill an order) after signature verification succeeds. Port of platform-go's
 * `stripe.EventHandler` — the `context.Context` is dropped (trace context rides the coroutine scope),
 * and the callback `suspend`s so it can await its own work. A `null` handler leaves the default
 * behavior (verify + observe + log) in place.
 */
public fun interface EventHandler {
    public suspend fun handle(event: Event)
}

/**
 * The seam through which the manager performs outbound Stripe operations. Production wires it to
 * [RealStripeApiClient] over a [StripeClient]; tests supply a stub so the params-building/adaptation
 * logic is exercised without any network delivery — the same "inject a narrowed client seam" move
 * `:analytics-segment` makes with its `MessageEnqueuer`.
 *
 * The methods are plain (blocking) because stripe-java is synchronous; the manager bridges them onto
 * coroutines with `withContext(Dispatchers.IO)`, the analog of how `:cache-redis` bridges Lettuce's
 * `RedisFuture` with `.await()`.
 */
internal interface StripeApiClient {
    fun createCustomer(input: CustomerCreationInput): String

    fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent

    fun createSubscription(input: SubscriptionCreationInput): String
}

/**
 * A Stripe-backed [PaymentManager] — the port of platform-go's `stripePaymentManager`.
 *
 * Each method opens an Observer span. The inbound [handleEventWebhook] caps the body, verifies the
 * signature with [Webhook.constructEvent], records the event id/type, enriches a `payment_intent.succeeded`
 * with the intent's id/amount/currency, logs any unhandled type, then hands the verified event to the
 * optional [EventHandler]. The outbound creates guard on a configured client, build a Stripe params
 * object, and drive it through the injected [StripeApiClient], recording the returned provider id.
 *
 * TODO(metrics): platform-go records nothing extra here beyond the span attributes this port already
 * mirrors; should a metrics pillar land in observability-api, per-operation counters would attach the
 * same way `:cache-api` left its `TODO(metrics)` seam.
 */
public class StripePaymentManager internal constructor(
    private val o11y: Observer,
    private val webhookSecret: String,
    private val client: StripeApiClient?,
    private val handler: EventHandler?,
) : PaymentManager {
    override suspend fun handleEventWebhook(
        payload: ByteArray,
        signatureHeader: String,
    ) {
        o11y.span("HandleEventWebhook") {
            require(payload.size <= MAX_WEBHOOK_BODY_BYTES) {
                "webhook body exceeds $MAX_WEBHOOK_BODY_BYTES bytes"
            }

            val event =
                try {
                    Webhook.constructEvent(payload.toString(Charsets.UTF_8), signatureHeader, webhookSecret)
                } catch (verificationError: SignatureVerificationException) {
                    throw error(verificationError, "verifying webhook signature")
                }

            set("stripe.event_id", event.id).set("stripe.event_type", event.type)

            when (event.type) {
                EVENT_PAYMENT_INTENT_SUCCEEDED -> {
                    val obj =
                        try {
                            event.dataObjectDeserializer.deserializeUnsafe()
                        } catch (decodeError: EventDataObjectDeserializationException) {
                            throw error(decodeError, "decoding payment intent")
                        }
                    if (obj is StripePaymentIntent) {
                        set("stripe.payment_intent_id", obj.id)
                            .set("stripe.amount", obj.amount)
                            .set("stripe.currency", obj.currency)
                    }
                }
                else -> {
                    set("event_type", event.type)
                    logger.info("Unhandled event type")
                }
            }

            // Hand the verified event to the consumer callback (if any) so it can act on it, rather
            // than decoding it here and dropping it on the floor. A thrown handler error propagates and
            // is recorded on the span by the enclosing `span { }` scope.
            val activeHandler = handler
            if (activeHandler != null) {
                activeHandler.handle(event)
            }
        }
    }

    override suspend fun createCustomer(input: CustomerCreationInput): String =
        o11y.span("CreateCustomer") {
            val api = client ?: throw error(ErrApiKeyNotConfigured, "creating customer")
            val id = withContext(Dispatchers.IO) { api.createCustomer(input) }
            set("stripe.customer_id", id)
            id
        }

    override suspend fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent =
        o11y.span("CreatePaymentIntent") {
            val api = client ?: throw error(ErrApiKeyNotConfigured, "creating payment intent")
            val intent = withContext(Dispatchers.IO) { api.createPaymentIntent(input) }
            set("stripe.payment_intent_id", intent.id)
            intent
        }

    override suspend fun createSubscription(input: SubscriptionCreationInput): String =
        o11y.span("CreateSubscription") {
            val api = client ?: throw error(ErrApiKeyNotConfigured, "creating subscription")
            val id = withContext(Dispatchers.IO) { api.createSubscription(input) }
            set("stripe.subscription_id", id)
            id
        }
}

/**
 * Builds a Stripe-backed [PaymentManager]. Port of platform-go's `ProvideStripePaymentManager`.
 *
 * When [StripeConfig.apiKey] is set, a [StripeClient] is initialized for outbound operations; otherwise
 * only the inbound webhook path works (a create then throws [ErrApiKeyNotConfigured]). [handler] is
 * optional and invoked for every verified event. Unlike Go's factory there is no nil-config branch —
 * [StripeConfig] is non-null by type — and, matching Go, the factory does not itself require the
 * webhook secret (see [StripeConfig.validate]).
 *
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 */
public fun StripePaymentManager(
    config: StripeConfig,
    handler: EventHandler? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): StripePaymentManager {
    val client =
        if (config.apiKey.isNotEmpty()) {
            RealStripeApiClient(StripeClient.builder().setApiKey(config.apiKey).build())
        } else {
            null
        }

    return StripePaymentManager(
        o11y = Observer(NAME, logger, tracerProvider),
        webhookSecret = config.webhookSecret,
        client = client,
        handler = handler,
    )
}

/** The production [StripeApiClient], translating each create into a stripe-java params object + call. */
internal class RealStripeApiClient(
    private val client: StripeClient,
) : StripeApiClient {
    override fun createCustomer(input: CustomerCreationInput): String {
        val params =
            CustomerCreateParams
                .builder()
                .apply {
                    if (input.email.isNotEmpty()) setEmail(input.email)
                    if (input.name.isNotEmpty()) setName(input.name)
                    if (input.metadata.isNotEmpty()) putAllMetadata(input.metadata)
                }.build()
        return client.customers().create(params, requestOptions(input.idempotencyKey)).id
    }

    override fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent {
        val params =
            PaymentIntentCreateParams
                .builder()
                .setAmount(input.amount)
                .setCurrency(input.currency)
                .apply {
                    if (input.customerID.isNotEmpty()) setCustomer(input.customerID)
                    if (input.description.isNotEmpty()) setDescription(input.description)
                    if (input.metadata.isNotEmpty()) putAllMetadata(input.metadata)
                }.build()
        val intent = client.paymentIntents().create(params, requestOptions(input.idempotencyKey))
        return PaymentIntent(id = intent.id, clientSecret = intent.clientSecret)
    }

    override fun createSubscription(input: SubscriptionCreationInput): String {
        val params =
            SubscriptionCreateParams
                .builder()
                .setCustomer(input.customerID)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(input.priceID).build())
                .apply {
                    if (input.metadata.isNotEmpty()) putAllMetadata(input.metadata)
                }.build()
        return client.subscriptions().create(params, requestOptions(input.idempotencyKey)).id
    }

    /**
     * Attaches an idempotency key when provided so a create is safely retryable — the analog of Go's
     * `applyRequestParams` calling `p.SetIdempotencyKey`. An empty key leaves the option unset.
     */
    private fun requestOptions(idempotencyKey: String): RequestOptions =
        RequestOptions
            .builder()
            .apply { if (idempotencyKey.isNotEmpty()) setIdempotencyKey(idempotencyKey) }
            .build()
}
