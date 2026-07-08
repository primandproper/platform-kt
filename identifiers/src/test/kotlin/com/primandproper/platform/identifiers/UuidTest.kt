package com.primandproper.platform.identifiers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UuidTest {
    @Test
    fun newUuidIsNotBlank() {
        assertFalse(newUuid().isBlank())
    }

    @Test
    fun newUuidIsThirtySixCharacters() {
        // 32 hex digits + 4 hyphens, e.g. 3fa85f64-5717-4562-b3fc-2c963f66afa6.
        assertEquals(36, newUuid().length)
    }

    @Test
    fun newUuidIsAlwaysValid() {
        assertTrue(isValidUuid(newUuid()))
    }

    @Test
    fun newUuidProducesUniqueValues() {
        val ids = List(1_000) { newUuid() }
        assertEquals(ids.size, ids.toSet().size, "every generated UUID must be unique")
    }

    @Test
    fun isValidUuidRejectsMalformedInput() {
        assertFalse(isValidUuid(""))
        assertFalse(isValidUuid("not-a-uuid"))
        assertFalse(isValidUuid(newUuid().replace("-", "")))
    }
}
