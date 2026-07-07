package com.primandproper.platform.secrets

import com.primandproper.platform.secrets.env.EnvSecretSource
import com.primandproper.platform.secrets.noop.NoopSecretSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of `secrets/config/config_test.go` (the ported providers only). */
class SecretsConfigTest {
    @Test
    fun `default and blank provider give env source`() {
        assertTrue(SecretsConfig().provideSecretSource() is EnvSecretSource)
        assertTrue(SecretsConfig(provider = "").provideSecretSource() is EnvSecretSource)
        assertTrue(SecretsConfig(provider = "ENV").provideSecretSource() is EnvSecretSource)
    }

    @Test
    fun `noop provider gives noop source`() {
        assertSame(NoopSecretSource, SecretsConfig(provider = "noop").provideSecretSource())
    }

    @Test
    fun `vendor providers are documented TODO seams`() {
        for (p in listOf("gcp", "ssm", "kubectl")) {
            assertFailsWith<NotImplementedError> { SecretsConfig(provider = p).provideSecretSource() }
        }
    }

    @Test
    fun `unknown provider throws`() {
        assertFailsWith<IllegalArgumentException> { SecretsConfig(provider = "vault").provideSecretSource() }
    }

    @Test
    fun `validate accepts known providers and rejects unknown`() {
        for (p in listOf("env", "noop", "gcp", "ssm", "kubectl", "")) {
            SecretsConfig(provider = p).validate()
        }
        assertFailsWith<IllegalArgumentException> { SecretsConfig(provider = "vault").validate() }
    }
}
