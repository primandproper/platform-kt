package com.primandproper.platform.authentication.totp

import java.net.URLEncoder

/**
 * Builds an `otpauth://totp/...` provisioning URI ([Key URI Format](https://github.com/google/google-authenticator/wiki/Key-Uri-Format))
 * that authenticator apps import — typically after it is rendered into a QR code. This port emits
 * only the URI string; QR rendering is left to a future `qrcodes` module (the analog of platform-go's
 * split between the token layer and its rendering).
 *
 * The label is `issuer:accountName`; the query carries the base32 [secret], the [issuer] (so apps
 * display it), and the [options]' algorithm, digit count, and period. All components are
 * percent-encoded.
 *
 * @param secret the base32-encoded shared secret to embed.
 */
public fun totpProvisioningUri(
    issuer: String,
    accountName: String,
    secret: String,
    options: TotpOptions = TotpOptions(),
): String {
    val label = "${encode(issuer)}:${encode(accountName)}"
    val query =
        listOf(
            "secret" to secret,
            "issuer" to issuer,
            "algorithm" to options.algorithm.name,
            "digits" to options.digits.value.toString(),
            "period" to options.periodSeconds.toString(),
        ).joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    return "otpauth://totp/$label?$query"
}

/** Percent-encodes [value] for a URI, emitting `%20` for spaces rather than form-encoding's `+`. */
private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
