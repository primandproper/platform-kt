/*
 * Common data builders for integration/load tests — the port of platform-go's top-level
 * `testutils` package (`testutil.go`). These are pure JVM helpers with no Docker dependency: an
 * arbitrary image (and its PNG encoding) plus a throwaway HTTP request, the fixtures repeated across
 * the repo's test suites.
 *
 * Go seeds `gofakeit` in an `init()` here so every fake-data helper shares one global PRNG; there is
 * no such global in this port (randomness lives in `:random`), so that seeding has no analog.
 */
package com.primandproper.platform.testutils

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpRequest
import javax.imageio.ImageIO

/** A made-up 32-byte secret for tests that need a fixed AES-256-sized key. Port of `Example32ByteKey`. */
public const val EXAMPLE_32_BYTE_KEY: String = "HEREISA32CHARSECRETWHICHISMADEUP"

/** A made-up 64-byte secret for tests that need a fixed HMAC-SHA512-sized key. Port of `Example64ByteKey`. */
public const val EXAMPLE_64_BYTE_KEY: String = "HEREISA64CHARSECRETWHICHISMADEUPHEREISA64CHARSECRETWHICHISMADEUP"

/**
 * Builds a square [BufferedImage] of [widthAndHeight] pixels a side, filled with a deterministic
 * gradient — each pixel is `RGBA(x, y, x+y, 255)` truncated to a byte, matching platform-go's
 * `BuildArbitraryImage`. Useful whenever a test just needs *some* real image bytes (uploads, image
 * processing) without caring about the contents.
 */
public fun buildArbitraryImage(widthAndHeight: Int): BufferedImage {
    val img = BufferedImage(widthAndHeight, widthAndHeight, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until widthAndHeight) {
        for (y in 0 until widthAndHeight) {
            val r = x and 0xFF
            val g = y and 0xFF
            val b = (x + y) and 0xFF
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            img.setRGB(x, y, argb)
        }
    }
    return img
}

/**
 * Builds the same gradient image as [buildArbitraryImage] and returns it alongside its PNG-encoded
 * bytes — the analog of `BuildArbitraryImagePNGBytes`, which returns `(image.Image, []byte)`.
 */
public fun buildArbitraryImagePngBytes(widthAndHeight: Int): Pair<BufferedImage, ByteArray> {
    val img = buildArbitraryImage(widthAndHeight)
    val buffer = ByteArrayOutputStream()
    check(ImageIO.write(img, "png", buffer)) { "testutils: no PNG ImageWriter available" }
    return img to buffer.toByteArray()
}

/**
 * Builds an arbitrary [HttpRequest] — an `OPTIONS` to a throwaway URL with no body, the analog of
 * `BuildTestRequest`. Uses the JDK's built-in `java.net.http` client so the harness stays free of a
 * third-party HTTP dependency (Go reaches for `net/http`; here the standard library suffices).
 */
public fun buildTestRequest(): HttpRequest =
    HttpRequest.newBuilder()
        .uri(URI.create("https://whatever.whocares.gov"))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .build()
