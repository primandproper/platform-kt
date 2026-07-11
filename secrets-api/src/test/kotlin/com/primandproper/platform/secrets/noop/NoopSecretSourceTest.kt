package com.primandproper.platform.secrets.noop

import com.primandproper.platform.secrets.SecretNotFoundException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoopSecretSourceTest {
    @Test
    fun `getSecret throws SecretNotFoundException carrying the key`() =
        runTest {
            val e = assertFailsWith<SecretNotFoundException> { NoopSecretSource.getSecret("any-key") }
            assertEquals("any-key", e.key)
        }

    @Test
    fun `close is a no-op`() =
        runTest {
            NoopSecretSource.close()
        }
}
