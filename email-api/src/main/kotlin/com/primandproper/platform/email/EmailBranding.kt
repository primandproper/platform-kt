package com.primandproper.platform.email

/**
 * App-specific branding used when building templated (Hermes) emails. Port of platform-go's
 * `email.EmailBranding`.
 *
 * TODO(hermes): platform-go's `email/config` renders message bodies through the `matcornic/hermes`
 * template engine (`Config.BuildHermes`). There is no Hermes equivalent on the JVM here, so template
 * rendering is a documented seam — callers supply pre-rendered [OutboundEmailMessage.htmlContent].
 * This type is carried so the branding inputs (company name, logo) survive the port for a future
 * templating backend.
 */
public data class EmailBranding(
    val companyName: String,
    val logoURL: String = "",
)
