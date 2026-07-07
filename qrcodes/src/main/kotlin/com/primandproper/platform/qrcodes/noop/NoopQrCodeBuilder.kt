package com.primandproper.platform.qrcodes.noop

import com.primandproper.platform.qrcodes.QrCodeBuilder

/**
 * A no-op [QrCodeBuilder]: every call returns an empty string. Port of platform-go's
 * `qrcodes/noop.Builder` — a safe default for wiring, and for tests that don't need a real QR code.
 */
public class NoopQrCodeBuilder : QrCodeBuilder {
    override suspend fun buildQrCode(
        username: String,
        twoFactorSecret: String,
    ): String = ""
}
