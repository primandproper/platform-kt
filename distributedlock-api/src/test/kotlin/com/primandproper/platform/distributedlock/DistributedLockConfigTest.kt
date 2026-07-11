package com.primandproper.platform.distributedlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Port of the provider-selection portion of platform-go's `distributedlock/config/config_test.go`. */
class DistributedLockConfigTest {
    @Test
    fun `known providers resolve from their string value`() {
        assertEquals(DistributedLockProvider.REDIS, DistributedLockProvider.fromValue("redis"))
        assertEquals(DistributedLockProvider.POSTGRES, DistributedLockProvider.fromValue("postgres"))
        assertEquals(DistributedLockProvider.MEMORY, DistributedLockProvider.fromValue("memory"))
        assertEquals(DistributedLockProvider.NOOP, DistributedLockProvider.fromValue("noop"))
    }

    @Test
    fun `value is normalized (trimmed, case-insensitive)`() {
        assertEquals(DistributedLockProvider.REDIS, DistributedLockProvider.fromValue("  REDIS "))
        assertEquals(DistributedLockProvider.MEMORY, DistributedLockProvider.fromValue("Memory"))
    }

    @Test
    fun `noop is an explicit opt-in, not a silent fallback`() {
        assertEquals(DistributedLockProvider.NOOP, DistributedLockProvider.fromValue("noop"))
    }

    @Test
    fun `unknown and empty providers throw rather than silently disabling locking`() {
        assertFailsWith<IllegalArgumentException> { DistributedLockProvider.fromValue("made-up") }
        assertFailsWith<IllegalArgumentException> { DistributedLockProvider.fromValue("") }
        assertFailsWith<IllegalArgumentException> { DistributedLockProvider.fromValue("   ") }
    }
}
