package com.primandproper.platform.secrets

import com.primandproper.platform.secrets.env.EnvSecretSource
import com.primandproper.platform.secrets.noop.NoopSecretSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of `secrets/config/config_test.go` (the ported providers only). */
class SecretsConfigTest {
    @Test
    fun `default provider gives env source`() {
        assertTrue(SecretsConfig().SecretSource() is EnvSecretSource)
        assertTrue(SecretsConfig(provider = SecretProvider.ENV).SecretSource() is EnvSecretSource)
    }

    @Test
    fun `noop provider gives noop source`() {
        assertSame(NoopSecretSource, SecretsConfig(provider = SecretProvider.NOOP).SecretSource())
    }

    @Test
    fun `vendor providers are documented TODO seams`() {
        for (p in listOf(SecretProvider.GCP, SecretProvider.SSM, SecretProvider.KUBECTL)) {
            assertFailsWith<NotImplementedError> { SecretsConfig(provider = p).SecretSource() }
        }
    }

    @Test
    fun `fromValue resolves known providers and returns null for unknown or blank`() {
        assertEquals(SecretProvider.ENV, SecretProvider.fromValue("ENV"))
        assertEquals(SecretProvider.NOOP, SecretProvider.fromValue("  noop "))
        assertNull(SecretProvider.fromValue("vault"))
        assertNull(SecretProvider.fromValue(""))
    }

    @Test
    fun `providerFromValue maps blank to env and rejects unknown`() {
        assertEquals(SecretProvider.ENV, SecretsConfig.providerFromValue(""))
        assertEquals(SecretProvider.ENV, SecretsConfig.providerFromValue("env"))
        assertEquals(SecretProvider.GCP, SecretsConfig.providerFromValue("gcp"))
        assertFailsWith<IllegalArgumentException> { SecretsConfig.providerFromValue("vault") }
    }
}
