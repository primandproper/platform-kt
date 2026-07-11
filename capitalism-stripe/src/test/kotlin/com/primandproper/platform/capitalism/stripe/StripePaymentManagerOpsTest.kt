package com.primandproper.platform.capitalism.stripe

import com.primandproper.platform.capitalism.Currency
import com.primandproper.platform.capitalism.CustomerCreationInput
import com.primandproper.platform.capitalism.PaymentIntent
import com.primandproper.platform.capitalism.PaymentIntentCreationInput
import com.primandproper.platform.capitalism.SubscriptionCreationInput
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.testing.RecordingObserver
import com.stripe.StripeClient
import com.stripe.model.Customer
import com.stripe.model.StripeObject
import com.stripe.model.StripeObjectInterface
import com.stripe.model.Subscription
import com.stripe.net.ApiMode
import com.stripe.net.ApiResource
import com.stripe.net.BaseAddress
import com.stripe.net.RequestOptions
import com.stripe.net.StripeResponseGetter
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.stripe.model.PaymentIntent as StripePaymentIntent

/**
 * Exercises the outbound operations without a live Stripe. Two layers are covered:
 *  - the manager's behavior (client-null guard, delegation, observed id) against a stubbed
 *    [StripeApiClient] seam — the "subscription lifecycle against a stubbed client" the port targets;
 *  - [RealStripeApiClient]'s params mapping against a capturing [StripeResponseGetter], the analog of
 *    platform-go's `stripe_ops_test.go` inspecting the request Stripe would have sent (there via an
 *    `httptest` server, here via the SDK's response-getter seam).
 */
class StripePaymentManagerOpsTest {
    // --- manager behavior against a stubbed seam ---------------------------------------------------

    private class StubApiClient(
        val customerId: String = "cus_test",
        val paymentIntent: PaymentIntent = PaymentIntent(id = "pi_test", clientSecret = "cs_test"),
        val subscriptionId: String = "sub_test",
        val fail: Boolean = false,
    ) : StripeApiClient {
        val customerInputs: MutableList<CustomerCreationInput> = mutableListOf()
        val paymentIntentInputs: MutableList<PaymentIntentCreationInput> = mutableListOf()
        val subscriptionInputs: MutableList<SubscriptionCreationInput> = mutableListOf()

        private fun maybeFail() {
            if (fail) throw RuntimeException("stripe rejected the request")
        }

        override fun createCustomer(input: CustomerCreationInput): String {
            customerInputs += input
            maybeFail()
            return customerId
        }

        override fun createPaymentIntent(input: PaymentIntentCreationInput): PaymentIntent {
            paymentIntentInputs += input
            maybeFail()
            return paymentIntent
        }

        override fun createSubscription(input: SubscriptionCreationInput): String {
            subscriptionInputs += input
            maybeFail()
            return subscriptionId
        }
    }

    private fun manager(
        client: StripeApiClient?,
        obs: RecordingObserver = RecordingObserver(),
    ): StripePaymentManager = StripePaymentManager(o11y = obs, webhookSecret = "whsec", client = client, handler = null)

    @Test
    fun `createSubscription delegates and observes the subscription id`() =
        runTest {
            val obs = RecordingObserver()
            val stub = StubApiClient()

            val id = manager(stub, obs).createSubscription(SubscriptionCreationInput(customerID = "cus_abc", priceID = "price_xyz"))

            assertEquals("sub_test", id)
            assertEquals("price_xyz", stub.subscriptionInputs.single().priceID)
            obs.assertObservedOperationWithValues("stripe.subscription_id" to "sub_test")
        }

    @Test
    fun `createCustomer delegates and observes the customer id`() =
        runTest {
            val obs = RecordingObserver()
            val stub = StubApiClient()

            val id = manager(stub, obs).createCustomer(CustomerCreationInput(email = "buyer@example.com"))

            assertEquals("cus_test", id)
            assertEquals("buyer@example.com", stub.customerInputs.single().email)
            obs.assertObservedOperationWithValues("stripe.customer_id" to "cus_test")
        }

    @Test
    fun `createPaymentIntent delegates and returns the intent`() =
        runTest {
            val stub = StubApiClient()

            val intent = manager(stub).createPaymentIntent(PaymentIntentCreationInput(amount = 1000, currency = Currency("usd")))

            assertEquals("pi_test", intent.id)
            assertEquals("cs_test", intent.clientSecret)
            assertEquals(1000, stub.paymentIntentInputs.single().amount)
        }

    @Test
    fun `outbound ops error without a configured API key`() =
        runTest {
            // Built via the public factory with no API key: the client seam is null.
            val pm = StripePaymentManager(StripeConfig(webhookSecret = "whsec"))

            val error = assertFailsWith<Throwable> { pm.createCustomer(CustomerCreationInput(email = "x@y.z")) }
            assertTrue(isError<ApiKeyNotConfiguredException>(error))
            assertFailsWith<Throwable> { pm.createSubscription(SubscriptionCreationInput(customerID = "c", priceID = "p")) }
            assertFailsWith<Throwable> { pm.createPaymentIntent(PaymentIntentCreationInput(amount = 1, currency = Currency("usd"))) }
        }

    @Test
    fun `outbound op surfaces a client rejection`() =
        runTest {
            val pm = manager(StubApiClient(fail = true))
            assertFailsWith<RuntimeException> { pm.createCustomer(CustomerCreationInput(email = "buyer@example.com")) }
        }

    // --- RealStripeApiClient params mapping against a capturing response getter --------------------

    private class CapturingResponseGetter(
        private val responder: (String) -> StripeObject,
    ) : StripeResponseGetter {
        data class Captured(
            val method: ApiResource.RequestMethod?,
            val path: String?,
            val params: Map<String, Any?>,
            val idempotencyKey: String?,
        )

        val captured: MutableList<Captured> = mutableListOf()

        @Suppress("UNCHECKED_CAST")
        override fun <T : StripeObjectInterface?> request(
            baseAddress: BaseAddress?,
            method: ApiResource.RequestMethod?,
            path: String?,
            params: MutableMap<String, Any>?,
            typeToken: Type?,
            options: RequestOptions?,
            apiMode: ApiMode?,
        ): T {
            captured += Captured(method, path, params ?: emptyMap(), options?.idempotencyKey)
            return responder(path ?: "") as T
        }

        override fun requestStream(
            baseAddress: BaseAddress?,
            method: ApiResource.RequestMethod?,
            path: String?,
            params: MutableMap<String, Any>?,
            options: RequestOptions?,
            apiMode: ApiMode?,
        ): java.io.InputStream = throw UnsupportedOperationException("streaming not used in tests")
    }

    private fun realClient(getter: CapturingResponseGetter): RealStripeApiClient = RealStripeApiClient(StripeClient(getter))

    @Test
    fun `createCustomer maps params and idempotency key onto the request`() {
        val getter = CapturingResponseGetter { Customer().apply { id = "cus_test" } }

        val id =
            realClient(getter).createCustomer(
                CustomerCreationInput(
                    email = "buyer@example.com",
                    name = "Buyer Person",
                    metadata = mapOf("tier" to "gold"),
                    idempotencyKey = "idem-cus-1",
                ),
            )

        assertEquals("cus_test", id)
        val req = getter.captured.single()
        assertEquals(ApiResource.RequestMethod.POST, req.method)
        assertTrue(req.path!!.contains("customers"))
        assertEquals("buyer@example.com", req.params["email"])
        assertEquals("Buyer Person", req.params["name"])
        assertEquals("idem-cus-1", req.idempotencyKey)
    }

    @Test
    fun `createPaymentIntent maps amount and currency onto the request`() {
        val getter =
            CapturingResponseGetter {
                StripePaymentIntent().apply {
                    id = "pi_test"
                    clientSecret = "cs_test"
                }
            }

        val intent =
            realClient(getter).createPaymentIntent(
                PaymentIntentCreationInput(amount = 1000, currency = Currency("usd"), customerID = "cus_abc", idempotencyKey = "idem-pi-1"),
            )

        assertEquals("pi_test", intent.id)
        assertEquals("cs_test", intent.clientSecret)
        val req = getter.captured.single()
        assertTrue(req.path!!.contains("payment_intents"))
        // The SDK's params converter may box the amount as any Number; compare by value.
        assertEquals(1000L, (req.params["amount"] as Number).toLong())
        assertEquals("usd", req.params["currency"])
        assertEquals("cus_abc", req.params["customer"])
        assertEquals("idem-pi-1", req.idempotencyKey)
    }

    @Test
    fun `createSubscription maps the customer and price item onto the request`() {
        val getter = CapturingResponseGetter { Subscription().apply { id = "sub_test" } }

        val id =
            realClient(getter).createSubscription(
                SubscriptionCreationInput(customerID = "cus_abc", priceID = "price_xyz", idempotencyKey = "idem-sub-1"),
            )

        assertEquals("sub_test", id)
        val req = getter.captured.single()
        assertTrue(req.path!!.contains("subscriptions"))
        assertEquals("cus_abc", req.params["customer"])
        assertEquals("idem-sub-1", req.idempotencyKey)
    }
}
