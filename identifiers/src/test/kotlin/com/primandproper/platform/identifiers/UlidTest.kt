package com.primandproper.platform.identifiers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UlidTest {
    @Test
    fun newUlidIsNotBlank() {
        assertFalse(newUlid().isBlank())
    }

    @Test
    fun newUlidIsTwentySixCharacters() {
        assertEquals(26, newUlid().length)
        assertEquals(ULID_LENGTH, newUlid().length)
    }

    @Test
    fun newUlidIsAlwaysValid() {
        assertTrue(isValidUlid(newUlid()))
    }

    @Test
    fun newUlidProducesUniqueValues() {
        val ids = List(1_000) { newUlid() }
        assertEquals(ids.size, ids.toSet().size, "every generated ULID must be unique")
    }

    @Test
    fun ulidsSortInCreationOrder() {
        val first = newUlid()
        Thread.sleep(5)
        val second = newUlid()
        Thread.sleep(5)
        val third = newUlid()

        val sorted = listOf(third, first, second).sorted()
        assertEquals(listOf(first, second, third), sorted, "ULIDs must sort lexicographically by creation time")
    }

    @Test
    fun isValidUlidRejectsWrongLength() {
        assertFalse(isValidUlid(""))
        assertFalse(isValidUlid(newUlid().dropLast(1)))
        assertFalse(isValidUlid(newUlid() + "0"))
    }

    @Test
    fun isValidUlidRejectsCharactersOutsideTheCrockfordAlphabet() {
        // I, L, O and U are deliberately excluded from Crockford's base32 alphabet. Mangle a
        // character after the first so the timestamp-range check isn't what fails the case.
        val id = newUlid()
        val mangled = id.substring(0, 1) + "I" + id.substring(2)
        assertFalse(isValidUlid(mangled))
    }

    @Test
    fun isValidUlidAcceptsLowercase() {
        assertTrue(isValidUlid(newUlid().lowercase()))
    }
}
