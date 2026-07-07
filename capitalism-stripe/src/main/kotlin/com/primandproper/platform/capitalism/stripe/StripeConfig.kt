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
     * Validates the config, returning a human-readable reason on failure or `null` when valid — the
     * analog of Go's `Config.ValidateWithContext`, which requires the webhook secret
     * (`validation.Field(&cfg.WebhookSecret, validation.Required)`). Note this is a config-time check;
     * the factory itself does not enforce it (Go builds a manager from an empty `&Config{}`), so a
     * caller that only performs outbound operations can skip it.
     */
    public fun validate(): String? {
        if (webhookSecret.isBlank()) return "webhook secret is required"
        return null
    }

    /** Whether this config passes [validate]. */
    public val isValid: Boolean get() = validate() == null
}
