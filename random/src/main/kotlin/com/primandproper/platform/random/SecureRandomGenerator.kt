package com.primandproper.platform.random

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.spanBlocking
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * The pluggable byte source behind [SecureRandomGenerator]. Mirrors the `io.Reader` field
 * (`randReader`) platform-go's `standardGenerator` holds — the seam its tests use to simulate a
 * failing or short-reading PRNG (`erroneousReader`, `shortReader` in `random_test.go`).
 */
public fun interface RandomSource {
    /** Returns exactly [length] fresh random bytes, or throws if it cannot. */
    public fun nextBytes(length: Int): ByteArray
}

/** The production [RandomSource]: a single reused [SecureRandom] instance. */
public object SecureRandomSource : RandomSource {
    private val secureRandom = SecureRandom()

    override fun nextBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }
}

/**
 * The production [RandomGenerator], backed by [SecureRandom] (or an injected [RandomSource] in
 * tests). Port of platform-go's `standardGenerator`.
 *
 * Every method opens a span via [Observer.spanBlocking], recording the requested length on both the
 * span and the running logger before generating — mirroring `g.o11y.Begin(ctx)` /
 * `op.Set(keys.LengthKey, length)`. `spanBlocking` records and rethrows any exception the block
 * raises, so a failing [RandomSource] surfaces on the span exactly once, without each method needing
 * its own try/catch (Go achieves the analogous single-recording behavior by routing `GenerateRawBytes`
 * through `observability.PrepareError` and the encoded-string variants through `op.Error` — two paths
 * to the same outcome that collapse into one here).
 */
public class SecureRandomGenerator internal constructor(
    private val o11y: Observer,
    private val source: RandomSource,
) : RandomGenerator {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching platform-go's
     *   `defaultGenerator := NewGenerator(loggingnoop.NewLogger(), ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param source the byte source; defaults to [SecureRandomSource]. Overridable for tests.
     */
    public constructor(
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
        source: RandomSource = SecureRandomSource,
    ) : this(Observer("random_generator", logger, tracerProvider), source)

    private fun generateSecret(length: Int): ByteArray = source.nextBytes(length)

    override fun generateRawBytes(length: Int): ByteArray =
        o11y.spanBlocking("GenerateRawBytes") {
            set(Keys.LENGTH, length)
            generateSecret(length)
        }

    override fun generateHexEncodedString(length: Int): String =
        o11y.spanBlocking("GenerateHexEncodedString") {
            set(Keys.LENGTH, length)
            HexFormat.of().formatHex(generateSecret(length))
        }

    override fun generateBase32EncodedString(length: Int): String =
        o11y.spanBlocking("GenerateBase32EncodedString") {
            set(Keys.LENGTH, length)
            base32Encode(generateSecret(length))
        }

    override fun generateBase64EncodedString(length: Int): String =
        o11y.spanBlocking("GenerateBase64EncodedString") {
            set(Keys.LENGTH, length)
            Base64.getUrlEncoder().withoutPadding().encodeToString(generateSecret(length))
        }

    override fun generateAlphabetEncodedString(
        alphabet: String,
        length: Int,
    ): String =
        o11y.spanBlocking("GenerateAlphabetEncodedString") {
            set(Keys.LENGTH, length)
            buildAlphabetString(alphabet, length) { source.nextBytes(1)[0].toInt() }
        }
}

/**
 * A ready-to-use [RandomGenerator] with noop logging/tracing, for one-off callers that don't want to
 * construct their own. Mirrors platform-go's package-level `defaultGenerator`.
 */
public val defaultRandomGenerator: RandomGenerator = SecureRandomGenerator()

/**
 * One-off hex-encoded random string via [defaultRandomGenerator]. Mirrors platform-go's package-level
 * `GenerateHexEncodedString` (Kotlin drops the separate `MustGenerateHexEncodedString` — both throw on
 * failure here, since there's no `(value, error)` return to distinguish them).
 */
public fun generateHexEncodedString(length: Int): String = defaultRandomGenerator.generateHexEncodedString(length)

/** One-off Base32-encoded random string via [defaultRandomGenerator]. */
public fun generateBase32EncodedString(length: Int): String = defaultRandomGenerator.generateBase32EncodedString(length)

/** One-off Base64url-encoded random string via [defaultRandomGenerator]. */
public fun generateBase64EncodedString(length: Int): String = defaultRandomGenerator.generateBase64EncodedString(length)

/** One-off raw random bytes via [defaultRandomGenerator]. */
public fun generateRawBytes(length: Int): ByteArray = defaultRandomGenerator.generateRawBytes(length)
