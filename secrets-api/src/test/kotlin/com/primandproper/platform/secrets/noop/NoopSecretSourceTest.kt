package com.primandproper.platform.secrets.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Port of `secrets/noop/noop_test.go`. */
class NoopSecretSourceTest {
    @Test
    fun `getSecret returns empty string`() =
        runTest {
            assertEquals("", NoopSecretSource.getSecret("any-key"))
        }

    @Test
    fun `close is a no-op`() {
        NoopSecretSource.close()
    }
}
