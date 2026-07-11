package com.primandproper.platform.qrcodes

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The production [QrCodeBuilder], backed by ZXing. Port of platform-go's `qrcodes.builder`.
 *
 * Every [buildQrCode] opens an [Observer] span, recording the username and the otpauth-URI length on
 * both pillars — mirroring `s.o11y.Begin(ctx)` plus `op.Set(keys.UsernameKey, ...)` and
 * `op.Set(keys.LengthKey, ...)`. A ZXing/encoding exception thrown inside the scope is recorded on the
 * span and rethrown by the [span] scope, the analog of Go's `observability.PrepareError` at each of its
 * encode/scale/PNG failure sites.
 *
 * The Go impl chains boombuler/barcode `qr.Encode(qr.L)` -> `barcode.Scale(256, 256)` -> `png.Encode`;
 * ZXing folds encode-and-size into a single `QRCodeWriter.encode(content, QR_CODE, size, size)`
 * producing a `BitMatrix`, which `MatrixToImageWriter` renders to PNG bytes. Error-correction level `L`
 * matches Go's `qr.L`. [size] generalizes Go's hard-coded 256x256 so callers can request a larger image.
 */
public class DefaultQrCodeBuilder internal constructor(
    private val issuer: String,
    private val size: Int,
    private val o11y: Observer,
) : QrCodeBuilder {
    /**
     * @param issuer the service name embedded in the `otpauth` label and `issuer` query parameter,
     *   shown by the authenticator app; the analog of Go's `Issuer`.
     * @param size the generated PNG's width and height in pixels; defaults to [DEFAULT_QR_SIZE] (256),
     *   matching Go's hard-coded `barcode.Scale(256, 256)`.
     * @param logger optional root logger; defaults to a noop logger, matching `NewBuilder(_, _, nil)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        issuer: String,
        size: Int = DEFAULT_QR_SIZE,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(issuer, size, Observer(NAME, logger, tracerProvider))

    override suspend fun buildQrCode(
        username: String,
        twoFactorSecret: String,
    ): String =
        o11y.span("BuildQRCode") {
            val otpString = buildOtpAuthUri(issuer, username, twoFactorSecret)
            set(Keys.USERNAME, username)
            set(Keys.LENGTH, otpString.length)

            val png = encodePng(otpString, size)
            "$BASE64_IMAGE_PREFIX${Base64.getEncoder().encodeToString(png)}"
        }

    private fun encodePng(
        content: String,
        pixels: Int,
    ): ByteArray {
        val hints =
            mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels, hints)
        val out = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", out)
        return out.toByteArray()
    }

    private companion object {
        const val NAME = "qr_code_builder"
        const val BASE64_IMAGE_PREFIX = "data:image/png;base64,"
    }
}

/**
 * Assembles the `otpauth://totp/{issuer:username}?issuer=...&secret=...` URI an authenticator app
 * scans. Port of the URI construction in platform-go's `BuildQRCode`: the label (`issuer:username`) is
 * path-escaped and the query values are form-encoded, so an issuer, username, or secret containing
 * spaces or reserved characters (`&`, `#`, `?`, ...) still produces a valid, correctly-parsed URI.
 *
 * Exposed `internal` so the same-module test can assert the escaping without driving a full encode —
 * the analog of the Go test swapping `qrEncode` to capture the otpauth string.
 */
internal fun buildOtpAuthUri(
    issuer: String,
    username: String,
    twoFactorSecret: String,
): String {
    val label = pathEscape("$issuer:$username")
    // Go's url.Values.Encode sorts keys, emitting `issuer` before `secret`; match that ordering.
    val query = "issuer=${formEncode(issuer)}&secret=${formEncode(twoFactorSecret)}"
    return "otpauth://totp/$label?$query"
}

/** Form-encodes a query value (space -> `+`, reserved chars -> `%XX`), matching Go's `url.Values.Encode`. */
private fun formEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

/**
 * Escapes a value for a URL path segment, the analog of Go's `url.PathEscape`. Built on the form
 * encoder with `+` rewritten to `%20`, since a path segment encodes a space as `%20`, not `+`.
 */
private fun pathEscape(value: String): String = formEncode(value).replace("+", "%20")
