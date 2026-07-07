package com.primandproper.platform.capitalism

/**
 * The supported payment providers. Port of platform-go's `config.StripeProvider` constant, modelled as
 * an enum so an unknown provider name is rejected the way Go's `validation.In(StripeProvider)` rejects
 * it. [value] is the wire/string form validated against configuration.
 */
public enum class PaymentProvider(
    public val value: String,
) {
    STRIPE("stripe"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `strings.TrimSpace(strings.ToLower(...))` normalize
         * followed by the `validation.In(StripeProvider)` check.
         */
        public fun fromValue(value: String): PaymentProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider-agnostic capitalism configuration. Port of the portable part of platform-go's
 * `config.Config`: whether payments are [enabled] and the chosen [provider].
 *
 * The Stripe credentials (`stripe.Config`) live with the backend in `:capitalism-stripe`
 * (`StripeConfig`), so this API module stays pure-JVM and free of any vendor dependency — the
 * `provideCapitalismImplementation` factory that unites them also lives there, matching how Go's
 * `capitalism/config` package sits above both `capitalism/noop` and `capitalism/stripe`.
 */
public data class CapitalismConfig(
    val enabled: Boolean = false,
    val provider: String = "",
) {
    /**
     * Validates the config, returning a human-readable reason on failure or `null` when valid — the
     * analog of Go's `Config.ValidateWithContext`. A disabled config always validates (Go returns
     * `nil` early when `!cfg.Enabled`); an enabled config must name a known [provider]
     * (`validation.In(StripeProvider)`).
     */
    public fun validate(): String? {
        if (!enabled) return null
        if (PaymentProvider.fromValue(provider) == null) {
            return "provider must be one of ${PaymentProvider.entries.map { it.value }}, was \"$provider\""
        }
        return null
    }

    /** Whether this config passes [validate]. */
    public val isValid: Boolean get() = validate() == null
}
