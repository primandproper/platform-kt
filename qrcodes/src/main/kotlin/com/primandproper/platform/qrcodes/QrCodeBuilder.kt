package com.primandproper.platform.qrcodes

/**
 * Generates QR codes for TOTP two-factor-authentication setup. Port of platform-go's `qrcodes.Builder`.
 *
 * [buildQrCode] renders the authenticator `otpauth://totp/...` URI for a username/secret pair into a
 * QR-code PNG and returns it as a base64 `data:image/png;base64,...` URI — the form an authenticator
 * app scans and a web or Android client drops straight into an image source. Go threads a
 * `context.Context`; this port suspends instead, so trace context propagates through the coroutine.
 *
 * It is an interface so the noop double (see the `noop` package) and unit tests can substitute a
 * builder without driving a live ZXing encoder.
 */
public interface QrCodeBuilder {
    /**
     * Builds the QR code for [username] and [twoFactorSecret], returning a base64 `data:image/png`
     * URI. Throws if the assembled `otpauth` URI is too large to fit in a QR code, or if PNG encoding
     * fails — the analog of the errors platform-go's `BuildQRCode` returns.
     */
    public suspend fun buildQrCode(
        username: String,
        twoFactorSecret: String,
    ): String
}
