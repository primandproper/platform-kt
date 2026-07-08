package com.primandproper.platform.numbers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RangeTest {
    @Test
    fun minRangeWithoutMaxIsValid() {
        MinRange(min = 1.0f).validate()
    }

    @Test
    fun minRangeWithZeroMinimumIsValid() {
        // Min is always present; a range starting at the type's zero must not be rejected.
        MinRange(min = 0.0f).validate()
        MinRange(min = 0).validate()
        MinRange(min = 0L).validate()
    }

    @Test
    fun minRangeWithMaxBelowMinIsInvalid() {
        assertFailsWith<IllegalArgumentException> { MinRange(min = 5, max = 2).validate() }
    }

    @Test
    fun minRangeWithMaxAtOrAboveMinIsValid() {
        MinRange(min = 5, max = 5).validate()
        MinRange(min = 5, max = 10).validate()
    }

    @Test
    fun minRangeIsAValue() {
        assertEquals(MinRange(min = 1, max = 3), MinRange(min = 1, max = 3))
    }

    @Test
    fun openRangeAllowsBothEndsOptional() {
        val empty = OpenRange<Int>()
        assertEquals(null, empty.min)
        assertEquals(null, empty.max)
        assertEquals(OpenRange(min = 1, max = 4), OpenRange(min = 1, max = 4))
    }
}
