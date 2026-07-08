package com.primandproper.platform.distributedlock.postgres

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `distributedlock/postgres/config_test.go`. */
class PostgresLockConfigTest {
    @Test
    fun `zero and explicit namespaces both validate`() {
        PostgresLockConfig().validate()
        PostgresLockConfig(namespace = 42).validate()
    }

    @Test
    fun `zero connWaitTimeout resolves to the default`() {
        assertEquals(DEFAULT_CONN_WAIT_TIMEOUT, PostgresLockConfig(connWaitTimeout = Duration.ZERO).effectiveConnWaitTimeout())
    }

    @Test
    fun `explicit connWaitTimeout is honored`() {
        assertEquals(2.seconds, PostgresLockConfig(connWaitTimeout = 2.seconds).effectiveConnWaitTimeout())
    }
}
