package com.primandproper.platform.cache.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TtlEnvelopeTest {
    @Test
    fun `encode then decode round-trips`() {
        val encoded = TtlEnvelope.encode(1234L, "payload with spaces")
        assertEquals(1234L to "payload with spaces", TtlEnvelope.decode(encoded))
    }

    @Test
    fun `decode of a malformed envelope is null`() {
        assertNull(TtlEnvelope.decode("no-separator"))
        assertNull(TtlEnvelope.decode("notanumber payload"))
    }

    @Test
    fun `a zero expiry never expires`() {
        assertFalse(TtlEnvelope.isExpired(0L, Long.MAX_VALUE))
    }

    @Test
    fun `an entry is expired at or after its expiry`() {
        assertFalse(TtlEnvelope.isExpired(100L, 99L))
        assertTrue(TtlEnvelope.isExpired(100L, 100L))
        assertTrue(TtlEnvelope.isExpired(100L, 101L))
    }
}
