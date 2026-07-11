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
         * names no known provider — the parse edge where a raw config string becomes the typed enum,
         * mirroring Go's `validation.In(...)` rejecting an unknown name. A blank value resolves to
         * `null` here; use [fromConfigValue] to apply the module's blank→noop policy.
         */
        public fun fromValue(value: String): EmailProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }

        /**
         * The parse edge for a configured provider string: a blank value resolves to `null` (the noop
         * emailer, mirroring Go's `ProvideEmailer` default branch), while a non-blank unknown name is
         * rejected loudly with [InvalidEmailProviderException].
         *
         * @throws InvalidEmailProviderException for a non-blank unknown provider.
         */
        public fun fromConfigValue(value: String): EmailProvider? =
            if (value.isBlank()) null else fromValue(value) ?: throw InvalidEmailProviderException(value)
    }
}

/** Thrown when a non-empty provider string names no known backend (see [EmailProvider.fromConfigValue]). */
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
 * Redis settings in `:cache-redis`. The "provider X requires its config block" check that platform-go
 * performs in `ValidateWithContext` happens at the wiring layer, where the concrete per-provider config
 * is in scope.
 *
 * [provider] is a typed [EmailProvider], resolved from its string form once at the parse edge
 * ([EmailProvider.fromConfigValue]); a `null` provider (the default) selects the noop emailer, mirroring
 * Go's `ProvideEmailer` default branch and its "empty provider is permitted for noop fallback" test.
 * Because the field is already typed, an unknown provider can never reach the config.
 */
public data class EmailConfig(
    val provider: EmailProvider? = null,
    val baseURL: String = "",
    val outboundInvitesEmailAddress: String = "",
    val passwordResetCreationEmailAddress: String = "",
    val passwordResetRedemptionEmailAddress: String = "",
)
