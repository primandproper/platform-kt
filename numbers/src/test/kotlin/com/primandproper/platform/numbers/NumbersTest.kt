package com.primandproper.platform.numbers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NumbersTest {
    @Test
    fun roundsPositiveToTwoDecimals() {
        assertEquals(3.14f, roundToDecimalPlaces(3.14159f, 2), 1e-6f)
    }

    @Test
    fun roundsToZeroDecimalsHalfAwayFromZero() {
        assertEquals(4.0f, roundToDecimalPlaces(3.7f, 0), 1e-6f)
        assertEquals(-4.0f, roundToDecimalPlaces(-3.7f, 0), 1e-6f)
    }

    @Test
    fun roundsNegativeToTwoDecimals() {
        assertEquals(-3.14f, roundToDecimalPlaces(-3.14159f, 2), 1e-6f)
    }

    @Test
    fun roundsZero() {
        assertEquals(0.0f, roundToDecimalPlaces(0.0f, 2), 1e-6f)
    }

    @Test
    fun roundsHalfUp() {
        // HALF_UP rounds a tie away from zero on both signs, matching Go's math.Round.
        assertEquals(2.56f, roundToDecimalPlaces(2.555f, 2), 0.01f)
        assertEquals(-2.56f, roundToDecimalPlaces(-2.555f, 2), 0.01f)
    }

    @Test
    fun roundsHalfDown() {
        assertEquals(2.55f, roundToDecimalPlaces(2.554f, 2), 0.01f)
    }

    @Test
    fun roundsLargeValueWithoutSaturation() {
        // 2e7 * 10^2 overflows an int32 intermediate; BigDecimal keeps the magnitude.
        assertEquals(2e7f, roundToDecimalPlaces(2e7f, 2), 1f)
        assertEquals(12345.68f, roundToDecimalPlaces(12345.6789f, 2), 0.01f)
    }

    @Test
    fun roundsHighPrecisionWithoutOverflowingToZero() {
        // A float32 multiplier saturates to +Inf near precision 39; BigDecimal does not.
        assertEquals(3.14f, roundToDecimalPlaces(3.14f, 39), 0.01f)
        assertEquals(1.23457f, roundToDecimalPlaces(1.23456789f, 5), 1e-5f)
    }

    @Test
    fun propagatesNonFiniteValuesInsteadOfThrowing() {
        // Non-finite floats have no BigDecimal form; they must pass through like Go's math.Round
        // instead of throwing NumberFormatException out of .toBigDecimal().
        assertTrue(roundToDecimalPlaces(Float.NaN, 2).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, roundToDecimalPlaces(Float.POSITIVE_INFINITY, 2))
        assertTrue(scale(Float.NaN, 2f).isNaN())
    }

    @Test
    fun rejectsNegativePrecision() {
        assertFailsWith<IllegalArgumentException> { roundToDecimalPlaces(1.0f, -1) }
    }

    @Test
    fun scaleDoublesWithDefaultPrecision() {
        assertEquals(5.0f, scale(2.5f, 2.0f), 1e-6f)
    }

    @Test
    fun scaleHalves() {
        assertEquals(2.0f, scale(4.0f, 0.5f), 1e-6f)
    }

    @Test
    fun scaleHonorsCustomPrecision() {
        assertEquals(9.999f, scale(3.333f, 3.0f, 3), 0.001f)
        assertEquals(5.0f, scale(2.7f, 2.0f, 0), 1e-6f)
    }

    @Test
    fun scaleByZeroFactorIsZero() {
        assertEquals(0.0f, scale(10.0f, 0.0f), 1e-6f)
    }

    @Test
    fun scaleNegativeValue() {
        assertEquals(-10.0f, scale(-5.0f, 2.0f), 1e-6f)
    }

    @Test
    fun scaleLargeValueWithoutSaturation() {
        assertEquals(4e7f, scale(2e7f, 2.0f), 1f)
    }

    @Test
    fun scaleToYieldScalesUp() {
        assertEquals(3.0f, scaleToYield(2.0f, 4, 6), 1e-6f)
        assertEquals(6.0f, scaleToYield(1.5f, 2, 8), 1e-6f)
    }

    @Test
    fun scaleToYieldScalesDown() {
        assertEquals(2.0f, scaleToYield(4.0f, 4, 2), 1e-6f)
    }

    @Test
    fun scaleToYieldSameYieldIsIdentity() {
        assertEquals(3.5f, scaleToYield(3.5f, 4, 4), 1e-6f)
    }

    @Test
    fun scaleToYieldHonorsCustomPrecision() {
        assertEquals(2.333f, scaleToYield(1.0f, 3, 7, 3), 0.001f)
        assertEquals(4.0f, scaleToYield(2.7f, 4, 6, 0), 1e-6f)
    }

    @Test
    fun scaleToYieldReturnsOriginalOnNonPositiveOriginalYield() {
        assertEquals(5.0f, scaleToYield(5.0f, 0, 10), 1e-6f)
        assertEquals(5.0f, scaleToYield(5.0f, -2, 10), 1e-6f)
    }

    @Test
    fun scaleToYieldZeroValue() {
        assertEquals(0.0f, scaleToYield(0.0f, 4, 8), 1e-6f)
    }
}
