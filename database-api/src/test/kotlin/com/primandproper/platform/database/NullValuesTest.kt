package com.primandproper.platform.database

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Port of the load-bearing conversions in platform-go's `database/null_values_test.go` — the
 * default-on-null readers and the float↔string NUMERIC codec. The pure pointer/uint-widening helpers
 * collapse into Kotlin nullability (see [NullValues]) and so are not separately tested.
 */
class NullValuesTest {
    @Test
    fun `timeOrEpoch returns the value when present`() {
        val now = Instant.parse("2026-07-07T12:00:00Z")
        assertEquals(now, NullValues.timeOrEpoch(now))
    }

    @Test
    fun `timeOrEpoch returns EPOCH when null`() {
        assertEquals(Instant.EPOCH, NullValues.timeOrEpoch(null))
    }

    @Test
    fun `stringOrEmpty passes through and defaults to empty`() {
        assertEquals("hello", NullValues.stringOrEmpty("hello"))
        assertEquals("", NullValues.stringOrEmpty(null))
    }

    @Test
    fun `boolOrFalse passes through and defaults to false`() {
        assertEquals(true, NullValues.boolOrFalse(true))
        assertEquals(false, NullValues.boolOrFalse(null))
    }

    @Test
    fun `floatFromString parses and defaults to zero`() {
        assertEquals(1.23f, NullValues.floatFromString("1.23"))
        assertEquals(0f, NullValues.floatFromString("not-a-number"))
    }

    @Test
    fun `floatFromNullableString parses or returns null`() {
        assertEquals(1.23f, NullValues.floatFromNullableString("1.23"))
        assertNull(NullValues.floatFromNullableString(null))
        assertNull(NullValues.floatFromNullableString("nope"))
    }

    @Test
    fun `doubleFromNullableString parses or returns null`() {
        assertEquals(1.23, NullValues.doubleFromNullableString("1.23"))
        assertNull(NullValues.doubleFromNullableString(null))
    }

    @Test
    fun `floatOrZeroFromNullableString parses or returns zero`() {
        assertEquals(1.23f, NullValues.floatOrZeroFromNullableString("1.23"))
        assertEquals(0f, NullValues.floatOrZeroFromNullableString(null))
    }

    @Test
    fun `stringFromFloat and stringFromDouble render minimal decimals`() {
        assertEquals("1.23", NullValues.stringFromFloat(1.23f))
        assertEquals("1.23", NullValues.stringFromDouble(1.23))
    }

    @Test
    fun `stringFromNullableFloat and Double keep null null`() {
        assertEquals("1.23", NullValues.stringFromNullableFloat(1.23f))
        assertNull(NullValues.stringFromNullableFloat(null))
        assertEquals("1.23", NullValues.stringFromNullableDouble(1.23))
        assertNull(NullValues.stringFromNullableDouble(null))
    }
}
