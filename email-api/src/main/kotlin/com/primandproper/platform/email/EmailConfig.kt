package com.primandproper.platform.email

/**
 * The supported email providers. Port of platform-go's `emailcfg.Provider*` constants. [value] is
 * the wire/string form validated against configuration.
 *
 * Only [RESEND] is implemented in this port (`:email-resend`); the rest are documented `TODO(<vendor>)`
 * seams — the enum still lists them so a config naming, say, `"sendgrid"` validates as a known
 * provider even before its backend lands, matching Go's `validation.In(...)` accepting all six names.
 */
public enum class EmailProvider(
    public val value: String,
) {
    SENDGRID("sendgrid"),
    MAILGUN("mailgun"),
    MAILJET("mailjet"),
    RESEND("resend"),
    POSTMARK("postmark"),
    SES("ses"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(...)` rejecting an unknown name.
         */
        public fun fromValue(value: String): EmailProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/** Thrown when [EmailConfig.validate] is given a non-empty provider that names no known backend. */
public class InvalidEmailProviderException(
    provider: String,
) : IllegalArgumentException("unknown email provider: $provider")

/**
 * Provider-agnostic email configuration. Port of the portable part of platform-go's
 * `emailcfg.Config`: the chosen [provider], the [baseURL] used when building templated emails, and
 * the standard outbound-address fields.
 *
 * The per-provider connection settings (e.g. `resend.Config`'s API token), the Hermes template
 * engine, and the circuit-breaker settings live with the backend modules (`ResendConfig` in
 * `:email-resend`), so this API module stays transport-free — matching how `:cache-api` keeps the
 * Redis settings in `:cache-redis`. Consequently [validate] here checks only that a non-empty
 * [provider] names a known backend; the "provider X requires its config block" check that
 * platform-go performs in `ValidateWithContext` happens at the wiring layer, where the concrete
 * per-provider config is in scope.
 *
 * An empty [provider] is permitted and selects the noop emailer, mirroring Go's
 * `ProvideEmailer` default branch and its "empty provider is permitted for noop fallback" test.
 */
public data class EmailConfig(
    val provider: String = "",
    val baseURL: String = "",
    val outboundInvitesEmailAddress: String = "",
    val passwordResetCreationEmailAddress: String = "",
    val passwordResetRedemptionEmailAddress: String = "",
) {
    /** Throws [InvalidEmailProviderException] when [provider] is non-empty yet unknown. */
    public fun validate() {
        if (provider.isBlank()) return
        EmailProvider.fromValue(provider) ?: throw InvalidEmailProviderException(provider)
    }
}
