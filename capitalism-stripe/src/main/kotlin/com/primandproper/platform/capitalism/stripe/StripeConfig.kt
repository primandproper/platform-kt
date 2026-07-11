package com.primandproper.platform.capitalism.stripe

/**
 * Configures the Stripe backend. Port of platform-go's `capitalism/stripe.Config`.
 *
 * The webhook path needs only [webhookSecret], so [apiKey] is optional at construction — a manager
 * built without an API key can still verify inbound webhooks, and only the outbound operations
 * (customer/payment-intent/subscription creation) require the key. That is exactly Go's split:
 * `ProvideStripePaymentManager` initializes the API client only `if cfg.APIKey != ""`.
 */
public data class StripeConfig(
    val apiKey: String = "",
    val webhookSecret: String = "",
) {
    /**
     * Validates the config, throwing [InvalidStripeConfigException] on failure — the analog of Go's
     * `Config.ValidateWithContext`, which requires the webhook secret
     * (`validation.Field(&cfg.WebhookSecret, validation.Required)`), and matching the throwing
     * `validate()` convention every other platform config uses. Note this is a config-time check;
     * the factory itself does not enforce it (Go builds a manager from an empty `&Config{}`), so a
     * caller that only performs outbound operations can skip it.
     */
    public fun validate() {
        if (webhookSecret.isBlank()) throw InvalidStripeConfigException("webhook secret is required")
    }
}

/** Thrown when [StripeConfig.validate] finds the config incomplete (e.g. a missing webhook secret). */
public class InvalidStripeConfigException(
    reason: String,
) : IllegalArgumentException(reason)
