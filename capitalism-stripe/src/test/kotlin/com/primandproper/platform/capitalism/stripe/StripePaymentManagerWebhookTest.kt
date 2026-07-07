package com.primandproper.platform.capitalism.stripe

import com.primandproper.platform.observability.testing.RecordingObserver
import com.stripe.exception.SignatureVerificationException
import kotlinx.coroutines.test.runTest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises the inbound webhook path without a live Stripe: a payload is signed locally with the
 * webhook secret (the same HMAC-SHA256 scheme Stripe uses) and round-tripped through
 * `Webhook.constructEvent`. Mirrors platform-go's `stripe.Test_stripePaymentManager_HandleSubscriptionEventWebhook`,
 * which uses `webhook.GenerateTestSignedPayload` for the same purpose.
 */
class StripePaymentManagerWebhookTest {
    private val secret = "whsec_test_secret"

    /** Produces a valid `Stripe-Signature` header for [payload] under [secret]; the local analog of Go's test signer. */
    private fun sign(
        payload: String,
        timestamp: Long = System.currentTimeMillis() / 1000,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal("$timestamp.$payload".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$hex"
    }

    private fun manager(
        obs: RecordingObserver = RecordingObserver(),
        handler: EventHandler? = null,
    ): StripePaymentManager = StripePaymentManager(o11y = obs, webhookSecret = secret, client = null, handler = handler)

    private fun paymentIntentEvent(
        id: String = "evt_1",
        piID: String = "pi_test",
        amount: Long = 1000,
    ): String =
        """{"id":"$id","object":"event","api_version":"2023-08-16","type":"payment_intent.succeeded",""" +
            """"data":{"object":{"id":"$piID","object":"payment_intent","amount":$amount,"currency":"usd"}}}"""

    @Test
    fun `verifies a signed payment_intent_succeeded and records the intent`() =
        runTest {
            val obs = RecordingObserver()
            val payload = paymentIntentEvent()

            manager(obs).handleEventWebhook(payload.toByteArray(), sign(payload))

            obs.assertObservedOperationWithValues(
                "stripe.event_type" to "payment_intent.succeeded",
                "stripe.payment_intent_id" to "pi_test",
                "stripe.amount" to 1000L,
                "stripe.currency" to "usd",
            )
        }

    @Test
    fun `rejects an invalid signature`() =
        runTest {
            val payload = "{}"
            assertFailsWith<SignatureVerificationException> {
                manager().handleEventWebhook(payload.toByteArray(), "t=1,v1=deadbeef")
            }
        }

    @Test
    fun `rejects an oversized body before verifying`() =
        runTest {
            val oversized = ByteArray((64 shl 10) + 1) { 'a'.code.toByte() }
            assertFailsWith<IllegalArgumentException> {
                manager().handleEventWebhook(oversized, "sig")
            }
        }

    @Test
    fun `records and logs an unhandled event type`() =
        runTest {
            val obs = RecordingObserver()
            val payload =
                """{"id":"evt_2","object":"event","api_version":"2023-08-16","type":"account.updated",""" +
                    """"data":{"object":{"id":"acct_1","object":"account"}}}"""

            manager(obs).handleEventWebhook(payload.toByteArray(), sign(payload))

            obs.assertObservedOperationWithValues("event_type" to "account.updated")
        }

    @Test
    fun `invokes the handler with the verified event`() =
        runTest {
            var gotID: String? = null
            val handler = EventHandler { event -> gotID = event.id }
            val payload = paymentIntentEvent(id = "evt_3")

            manager(handler = handler).handleEventWebhook(payload.toByteArray(), sign(payload))

            assertEquals("evt_3", gotID)
        }

    @Test
    fun `propagates a handler error`() =
        runTest {
            val handler = EventHandler { throw HandlerBoom() }
            val payload = paymentIntentEvent()

            assertFailsWith<HandlerBoom> {
                manager(handler = handler).handleEventWebhook(payload.toByteArray(), sign(payload))
            }
        }

    private class HandlerBoom : RuntimeException("handler boom")
}
