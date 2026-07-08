package com.primandproper.platform.distributedlock

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `unknown and empty providers fall back to noop`() {
        assertEquals(DistributedLockProvider.NOOP, DistributedLockProvider.fromValue("made-up"))
        assertEquals(DistributedLockProvider.NOOP, DistributedLockProvider.fromValue(""))
        assertEquals(DistributedLockProvider.NOOP, DistributedLockProvider.fromValue("   "))
    }
}
