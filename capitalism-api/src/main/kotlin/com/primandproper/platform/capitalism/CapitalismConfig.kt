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

/** Thrown when an enabled [CapitalismConfig] has no [PaymentProvider] selected. */
public class InvalidPaymentProviderException :
    IllegalArgumentException(
        "an enabled capitalism config requires a payment provider, one of ${PaymentProvider.entries.map { it.value }}",
    )

/**
 * Provider-agnostic capitalism configuration. Port of the portable part of platform-go's
 * `config.Config`: whether payments are [enabled] and the chosen [provider].
 *
 * [provider] is a typed [PaymentProvider], resolved from its string form once at the parse edge
 * ([PaymentProvider.fromValue]); `null` means no provider is selected. Because the field is already
 * typed, an unknown provider can never reach the config — only the "enabled without a provider" case
 * remains for [validate] to reject.
 *
 * The Stripe credentials (`stripe.Config`) live with the backend in `:capitalism-stripe`
 * (`StripeConfig`), so this API module stays pure-JVM and free of any vendor dependency — the
 * `PaymentManager` factory that unites them also lives there, matching how Go's
 * `capitalism/config` package sits above both `capitalism/noop` and `capitalism/stripe`.
 */
public data class CapitalismConfig(
    val enabled: Boolean = false,
    val provider: PaymentProvider? = null,
) {
    /**
     * Validates the config, throwing on failure — the analog of Go's `Config.ValidateWithContext`,
     * and matching the throwing `validate()` convention every other platform config uses (e.g.
     * `EmailConfig`, `LlmConfig`). A disabled config always validates (Go returns `nil` early when
     * `!cfg.Enabled`); an enabled config must have a [provider] selected (`validation.In(StripeProvider)`),
     * else [InvalidPaymentProviderException] is thrown.
     */
    public fun validate() {
        if (!enabled) return
        if (provider == null) throw InvalidPaymentProviderException()
    }
}
