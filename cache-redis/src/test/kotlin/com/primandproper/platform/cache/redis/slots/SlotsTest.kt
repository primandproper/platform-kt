package com.primandproper.platform.cache.redis.slots

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of platform-go's `cache/redis/slots/crc16_test.go`. */
class SlotsTest {
    // A deliberately naive bit-by-bit CRC16-CCITT (XMODEM), an independent oracle for the table-driven
    // implementation. Mirrors Go's `crc16Reference`.
    private fun crc16Reference(s: String): Int {
        var crc = 0
        for (ch in s) {
            crc = crc xor ((ch.code and 0xff) shl 8)
            repeat(8) {
                crc =
                    if (crc and 0x8000 != 0) {
                        ((crc shl 1) xor 0x1021) and 0xffff
                    } else {
                        (crc shl 1) and 0xffff
                    }
            }
        }
        return crc and 0xffff
    }

    @Test
    fun `empty input hashes to zero`() {
        assertEquals(0, slot(""))
    }

    @Test
    fun `output is always within the slot range`() {
        val rng = Random(42)
        repeat(256) {
            val len = 1 + rng.nextInt(64)
            val s = String(CharArray(len) { rng.nextInt(256).toChar() })
            assertTrue(slot(s) < SLOT_COUNT)
        }
    }

    @Test
    fun `identical input produces identical slot`() {
        assertEquals(slot("key:version42"), slot("key:version42"))
    }

    @Test
    fun `matches the reference implementation`() {
        val rng = Random(0xC0FFEE)
        repeat(1000) {
            val len = 1 + rng.nextInt(32)
            val s = String(CharArray(len) { rng.nextInt(256).toChar() })
            assertEquals(crc16Reference(s) % SLOT_COUNT, slot(s), "input: $s")
        }
    }

    @Test
    fun `slotForKey applies the hashtag extraction rule`() {
        assertEquals(slot("foo"), slotForKey("foo"))
        assertEquals(slot("user1000"), slotForKey("{user1000}.following"))
        assertEquals(slot("user1000"), slotForKey("{user1000}.followers"))
        assertEquals(slot("{}foo"), slotForKey("{}foo"))
        assertEquals(slot("{foo"), slotForKey("{foo"))
        assertEquals(slot("first"), slotForKey("{first}{second}"))
    }

    @Test
    fun `known Redis keyslots`() {
        // Values Redis itself reports via CLUSTER KEYSLOT, pinning the CRC16 to Redis's implementation.
        assertEquals(12182, slot("foo"))
        assertEquals(5061, slot("bar"))
        assertEquals(3443, slotForKey("{user1000}.following"))
    }
}
