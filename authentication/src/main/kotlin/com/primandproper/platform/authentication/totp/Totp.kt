package com.primandproper.platform.authentication.totp

import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The HMAC hash a TOTP uses. RFC 6238 permits SHA-1, SHA-256, and SHA-512; SHA-1 is the default and
 * the only algorithm most authenticator apps support. Mirrors `pquerna/otp`'s `Algorithm`.
 */
public enum class TotpAlgorithm(
    internal val macName: String,
) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}

/** The number of digits in a generated code. Mirrors `pquerna/otp`'s `Digits`. */
public enum class TotpDigits(
    public val value: Int,
) {
    SIX(6),
    EIGHT(8),
}

/**
 * TOTP generation/validation parameters. The defaults (30-second period, ±1 window skew, 6 digits,
 * SHA-1) match `pquerna/otp`'s defaults — which is what platform-go's `totp` verifier relies on.
 */
public data class TotpOptions(
    val periodSeconds: Long = 30,
    val skew: Int = 1,
    val digits: TotpDigits = TotpDigits.SIX,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
)

private val DEFAULT_OPTIONS = TotpOptions()

/** Generates the default (SHA-1, 6-digit, 30-second) TOTP code for [secret] at [time]. */
public fun generateTotpCode(
    secret: String,
    time: Instant,
): String = generateTotpCode(secret, time, DEFAULT_OPTIONS)

/**
 * Generates the TOTP code for [secret] at [time] under [options]. Port of `pquerna/otp`'s
 * `totp.GenerateCodeCustom`: [secret] is a base32-encoded shared secret (padding optional, case
 * insensitive), and the counter is `floor(epochSeconds / period)`.
 */
public fun generateTotpCode(
    secret: String,
    time: Instant,
    options: TotpOptions,
): String {
    val counter = time.epochSecond / options.periodSeconds
    return hotp(decodeBase32(secret), counter, options.digits, options.algorithm)
}

/** Validates [code] against [secret] at [time] with the default options. */
public fun validateTotpCode(
    code: String,
    secret: String,
    time: Instant,
): Boolean = validateTotpCode(code, secret, time, DEFAULT_OPTIONS)

/**
 * Validates [code] against [secret] at [time], accepting any counter within `±skew` windows of the
 * current one. Port of `pquerna/otp`'s `totp.ValidateCustom` (which defaults `Skew` to 1), so a code
 * from the immediately preceding or following period still passes — tolerating clock drift without a
 * real sleep in tests.
 */
public fun validateTotpCode(
    code: String,
    secret: String,
    time: Instant,
    options: TotpOptions,
): Boolean {
    // pquerna/otp's totp.Validate discards a base32 decode error and reports the code invalid, so a
    // malformed stored secret is a normal auth failure (→ ErrInvalidCode), not a thrown exception out
    // of the verifier. Generation (generateTotpCode) still throws — its secret is trusted.
    val key =
        try {
            decodeBase32(secret)
        } catch (_: IllegalArgumentException) {
            return false
        }
    val counter = time.epochSecond / options.periodSeconds
    for (offset in -options.skew..options.skew) {
        val candidate = hotp(key, counter + offset, options.digits, options.algorithm)
        // Constant-time compare per candidate, matching pquerna's subtle.ConstantTimeCompare.
        if (constantTimeEquals(candidate, code)) return true
    }
    return false
}

/** RFC 4226 HOTP: HMAC over the big-endian counter, dynamically truncated to [digits] decimal digits. */
private fun hotp(
    key: ByteArray,
    counter: Long,
    digits: TotpDigits,
    algorithm: TotpAlgorithm,
): String {
    val counterBytes = ByteArray(8)
    var c = counter
    for (i in 7 downTo 0) {
        counterBytes[i] = (c and 0xFF).toByte()
        c = c ushr 8
    }

    val mac = Mac.getInstance(algorithm.macName)
    mac.init(SecretKeySpec(key, algorithm.macName))
    val digest = mac.doFinal(counterBytes)

    val offset = (digest[digest.size - 1].toInt() and 0x0F)
    val binary =
        ((digest[offset].toInt() and 0x7F) shl 24) or
            ((digest[offset + 1].toInt() and 0xFF) shl 16) or
            ((digest[offset + 2].toInt() and 0xFF) shl 8) or
            (digest[offset + 3].toInt() and 0xFF)

    val modulo = MODULI[digits.value]
    return (binary % modulo).toString().padStart(digits.value, '0')
}

private val MODULI = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/**
 * Decodes an RFC 4648 base32 [secret] into raw bytes. Uppercases and drops padding first, matching
 * `pquerna/otp`'s normalization (it pads to a multiple of 8 then decodes with `StdEncoding`); padding
 * carries no bits, so ignoring it yields the identical byte string.
 */
private fun decodeBase32(secret: String): ByteArray {
    val normalized = secret.trim().uppercase().trimEnd('=')
    val out = ArrayList<Byte>(normalized.length * 5 / 8)
    var buffer = 0
    var bitsInBuffer = 0
    for (ch in normalized) {
        val index = BASE32_ALPHABET.indexOf(ch)
        require(index >= 0) { "invalid base32 character: '$ch'" }
        buffer = (buffer shl 5) or index
        bitsInBuffer += 5
        if (bitsInBuffer >= 8) {
            bitsInBuffer -= 8
            out.add(((buffer ushr bitsInBuffer) and 0xFF).toByte())
        }
    }
    return out.toByteArray()
}

/** Length-aware constant-time string comparison, mirroring `subtle.ConstantTimeCompare`. */
private fun constantTimeEquals(
    a: String,
    b: String,
): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].code xor b[i].code)
    }
    return diff == 0
}
