package com.primandproper.platform.bitmask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitmaskTest {
    // Mirrors platform-go's testPerm (uint8): 1<<iota for Read/Write/Delete/Admin.
    private val read = 1uL
    private val write = 2uL
    private val delete = 4uL
    private val admin = 8uL

    private fun mask(vararg flags: ULong) = Bitmask.of(8, *flags)

    // --- of / fromValue ---

    @Test
    fun ofWithNoFlagsIsEmpty() {
        val m = mask()
        assertEquals(0uL, m.value)
        assertTrue(m.isEmpty())
    }

    @Test
    fun ofWithMultipleFlagsCombines() {
        assertEquals(read or write, mask(read, write).value)
    }

    @Test
    fun ofWithDuplicateFlagsIsIdempotent() {
        assertEquals(read, mask(read, read).value)
    }

    @Test
    fun fromValueDecodesBits() {
        val m = Bitmask.of(8, 5uL)
        assertTrue(m.has(read))
        assertTrue(m.has(delete))
        assertFalse(m.has(write))
    }

    @Test
    fun ofMasksValueToWidth() {
        // 0x1FF (511) does not fit 8 bits; the raw factory masks to 0xFF.
        assertEquals(0xFFuL, Bitmask.of(8, 0x1FFuL).value)
    }

    @Test
    fun ofRejectsUnsupportedWidth() {
        assertFailsWith<IllegalArgumentException> { Bitmask.of(7, read) }
    }

    // --- set ---

    @Test
    fun setAddsFlags() {
        val m = mask().set(read, write)
        assertTrue(m.has(read))
        assertTrue(m.has(write))
    }

    @Test
    fun setDoesNotMutateOriginal() {
        val original = mask(read)
        original.set(write)
        assertFalse(original.has(write))
        assertEquals(read, original.value)
    }

    // --- clear ---

    @Test
    fun clearRemovesFlags() {
        val m = mask(read, write, delete).clear(read, write)
        assertFalse(m.has(read))
        assertFalse(m.has(write))
        assertTrue(m.has(delete))
    }

    @Test
    fun clearUnsetFlagIsNoOp() {
        assertEquals(read, mask(read).clear(write).value)
    }

    @Test
    fun clearDoesNotMutateOriginal() {
        val original = mask(read, write)
        original.clear(write)
        assertTrue(original.has(write))
    }

    // --- toggle ---

    @Test
    fun toggleFlipsFlags() {
        val m = mask(read).toggle(read, write)
        assertFalse(m.has(read))
        assertTrue(m.has(write))
    }

    @Test
    fun toggleDoesNotMutateOriginal() {
        val original = mask(read)
        original.toggle(read)
        assertTrue(original.has(read))
    }

    // --- has / hasAll / hasAny ---

    @Test
    fun hasReportsMembership() {
        val m = mask(read, write)
        assertTrue(m.has(read))
        assertFalse(m.has(delete))
    }

    @Test
    fun hasZeroFlagIsFalse() {
        assertFalse(mask(read).has(0uL))
    }

    @Test
    fun hasAllRequiresEveryFlag() {
        assertTrue(mask(read, write, delete).hasAll(read, write))
        assertFalse(mask(read).hasAll(read, write))
    }

    @Test
    fun hasAllWithNoFlagsIsFalse() {
        assertFalse(mask(read).hasAll())
        assertFalse(mask(read).hasAll(0uL))
    }

    @Test
    fun hasAnyRequiresOneFlag() {
        assertTrue(mask(read).hasAny(read, write))
        assertFalse(mask(read).hasAny(write, delete))
    }

    @Test
    fun hasAnyWithNoFlagsIsFalse() {
        assertFalse(mask(read).hasAny())
    }

    // --- isEmpty / count ---

    @Test
    fun isEmptyReflectsBits() {
        assertTrue(mask().isEmpty())
        assertFalse(mask(read).isEmpty())
    }

    @Test
    fun countCountsSetBits() {
        assertEquals(0, mask().count())
        assertEquals(3, mask(read, write, delete).count())
        assertEquals(8, Bitmask.of(8, 0xFFuL).count())
    }

    // --- union / intersect / difference ---

    @Test
    fun unionCombinesBoth() {
        val result = mask(read).union(mask(write))
        assertTrue(result.has(read))
        assertTrue(result.has(write))
    }

    @Test
    fun unionWithSelfIsIdentity() {
        val a = mask(read, write)
        assertEquals(a.value, a.union(a).value)
    }

    @Test
    fun intersectKeepsCommonBits() {
        val result = mask(read, write).intersect(mask(write, delete))
        assertFalse(result.has(read))
        assertTrue(result.has(write))
        assertFalse(result.has(delete))
    }

    @Test
    fun intersectWithNoOverlapIsEmpty() {
        assertTrue(mask(read).intersect(mask(write)).isEmpty())
    }

    @Test
    fun differenceRemovesOtherBits() {
        val result = mask(read, write, delete).difference(mask(write))
        assertTrue(result.has(read))
        assertFalse(result.has(write))
        assertTrue(result.has(delete))
    }

    @Test
    fun differenceWithSelfIsEmpty() {
        val a = mask(read, write)
        assertTrue(a.difference(a).isEmpty())
    }

    @Test
    fun combiningMismatchedWidthsFails() {
        assertFailsWith<IllegalArgumentException> { Bitmask.of(8, read).union(Bitmask.of(16, read)) }
    }

    // --- immutability: a mutation returns a new value, the original is intact ---

    @Test
    fun chainedOperationsLeaveEachStageIntact() {
        val a = mask(read)
        val b = a.set(write)
        val c = b.clear(read)

        assertEquals(read, a.value)
        assertEquals(read or write, b.value)
        assertEquals(write, c.value)
    }

    // --- toString ---

    @Test
    fun toStringIsZeroPaddedBinaryOfWidth() {
        assertEquals("00000000", mask().toString())
        assertEquals("00000001", mask(read).toString())
        assertEquals("00000011", mask(read, write).toString())
        assertEquals("11111111", Bitmask.of(8, 0xFFuL).toString())
    }

    @Test
    fun toStringHonoursWiderWidths() {
        assertEquals("0000000000000101", Bitmask.of(16, 1uL, 4uL).toString())
        assertEquals(32, Bitmask.of(32, 1uL).toString().length)
    }

    // --- JSON-number encode / decode ---

    @Test
    fun toJsonNumberEncodesAsBareDecimal() {
        assertEquals("3", mask(read, write).toJsonNumber())
        assertEquals("0", mask().toJsonNumber())
    }

    @Test
    fun parseJsonNumberDecodesBareDecimal() {
        val m = Bitmask.parseJsonNumber(8, "3")
        assertTrue(m.has(read))
        assertTrue(m.has(write))
    }

    @Test
    fun parseJsonNumberAcceptsWidthMaximum() {
        assertEquals(255uL, Bitmask.parseJsonNumber(8, "255").value)
    }

    @Test
    fun parseJsonNumberRejectsValueExceedingWidth() {
        // 511 must error rather than truncate to 255.
        assertFailsWith<IllegalArgumentException> { Bitmask.parseJsonNumber(8, "511") }
    }

    @Test
    fun parseJsonNumberRejectsNonNumeric() {
        assertFailsWith<IllegalArgumentException> { Bitmask.parseJsonNumber(8, "not a number") }
    }

    @Test
    fun parseJsonNumberRejectsNegative() {
        assertFailsWith<IllegalArgumentException> { Bitmask.parseJsonNumber(8, "-1") }
    }

    @Test
    fun jsonNumberRoundTrips() {
        val original = mask(read, write, admin)
        val restored = Bitmask.parseJsonNumber(8, original.toJsonNumber())
        assertEquals(original.value, restored.value)
    }
}
