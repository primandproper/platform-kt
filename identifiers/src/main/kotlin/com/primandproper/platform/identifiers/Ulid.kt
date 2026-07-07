package com.primandproper.platform.identifiers

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Crockford's base32 alphabet: 32 symbols, digits and uppercase letters with `I`, `L`, `O`, `U`
 * dropped to avoid transcription mix-ups with `1`/`0`/`V`.
 */
private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

private const val TIME_CHARS = 10 // 48-bit timestamp, 5 bits/char
private const val RANDOM_CHARS = 16 // 80-bit randomness, 5 bits/char

/** Length of every string [newUlid] returns and the only length [isValidUlid] accepts. */
public const val ULID_LENGTH: Int = TIME_CHARS + RANDOM_CHARS

private val secureRandom = SecureRandom()

/**
 * Produces a new [ULID](https://github.com/ulid/spec): a 26-character, Crockford-base32,
 * lexicographically sortable string built from a 48-bit millisecond timestamp followed by 80 bits
 * of cryptographically random entropy. This is the closest dependency-free stand-in for
 * platform-go's xid-backed `identifiers.New()` — both are compact, URL-safe, and sort in creation
 * order — though unlike xid this isn't monotonic within the same millisecond.
 */
public fun newUlid(): String {
    val randomBytes = ByteArray(10)
    secureRandom.nextBytes(randomBytes)
    return encodeTime(System.currentTimeMillis()) + encodeRandom(randomBytes)
}

/**
 * Reports whether [value] is a well-formed ULID: the right length, drawn only from the Crockford
 * alphabet, and with a timestamp that fits the 48-bit budget. Mirrors platform-go's
 * `identifiers.Validate`, trading its `error` return for a Boolean since there's nothing richer to
 * report for a malformed ID.
 */
public fun isValidUlid(value: String): Boolean {
    if (value.length != ULID_LENGTH) return false
    val upper = value.uppercase()
    // The first symbol only ever carries the top bits of a 48-bit timestamp packed into 10
    // 5-bit groups (50 bits), so it must be in 0-7 or the value overflows what we ever emit.
    if (upper[0] !in '0'..'7') return false
    return upper.all { it in ENCODING }
}

private fun encodeTime(timeMillis: Long): String {
    val chars = CharArray(TIME_CHARS)
    var remaining = timeMillis
    for (i in TIME_CHARS - 1 downTo 0) {
        chars[i] = ENCODING[(remaining and 0x1F).toInt()]
        remaining = remaining ushr 5
    }
    return String(chars)
}

private fun encodeRandom(bytes: ByteArray): String {
    var remaining = BigInteger(1, bytes)
    val mask = BigInteger.valueOf(0x1F)
    val chars = CharArray(RANDOM_CHARS)
    for (i in RANDOM_CHARS - 1 downTo 0) {
        chars[i] = ENCODING[remaining.and(mask).toInt()]
        remaining = remaining.shiftRight(5)
    }
    return String(chars)
}
