/*
 * numbers — numeric helpers for rounding, scaling, and yield-adjustment calculations, a port of
 * platform-go's `numbers` package.
 *
 * Rounding runs through java.math.BigDecimal with RoundingMode.HALF_UP (half away from zero), which
 * matches Go's math.Round without the float saturation the Go implementation works around by
 * computing in float64. Kotlin's stdlib already provides clamping through coerceIn / coerceAtLeast /
 * coerceAtMost, so there is deliberately no Clamp analog here.
 */
package com.primandproper.platform.numbers

import java.math.RoundingMode

/** Default number of decimal places [scale] and [scaleToYield] round to, matching Go's variadic default. */
private const val DEFAULT_PRECISION = 2

/**
 * Rounds [value] to [precision] decimal places, half away from zero. The value is taken through its
 * shortest round-tripping decimal string so the scaling is exact rather than reflecting the float's
 * binary noise; unlike Go's float64 intermediate there is no precision at which the multiplier
 * saturates, so large magnitudes and high precisions are both safe. [precision] must not be negative.
 */
public fun roundToDecimalPlaces(
    value: Float,
    precision: Int,
): Float {
    require(precision >= 0) { "precision must not be negative" }
    // NaN/±Infinity have no BigDecimal representation; propagate them like Go's float64 math.Round
    // (math.Round(NaN)=NaN) instead of throwing NumberFormatException out of `.toBigDecimal()`.
    if (!value.isFinite()) return value
    return value.toString().toBigDecimal().setScale(precision, RoundingMode.HALF_UP).toFloat()
}

/**
 * Multiplies [value] by [factor] and rounds to [precision] decimal places (default 2). Useful for
 * scaling a quantity: `scale(2.5f, 2.0f)` returns `5.0f`.
 */
public fun scale(
    value: Float,
    factor: Float,
    precision: Int = DEFAULT_PRECISION,
): Float = roundToDecimalPlaces(value * factor, precision)

/**
 * Scales [originalValue] from [originalYield] to [desiredYield]: `scaleToYield(2.0f, 4, 6)` returns
 * `3.0f`. A non-positive [originalYield] would divide by zero, so the original value is returned
 * unchanged, mirroring platform-go. The optional [precision] defaults to 2.
 */
public fun scaleToYield(
    originalValue: Float,
    originalYield: Int,
    desiredYield: Int,
    precision: Int = DEFAULT_PRECISION,
): Float {
    if (originalYield <= 0) return originalValue

    val factor = desiredYield.toFloat() / originalYield.toFloat()
    return scale(originalValue, factor, precision)
}
