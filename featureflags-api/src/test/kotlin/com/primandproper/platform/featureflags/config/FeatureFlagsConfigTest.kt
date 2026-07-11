package com.primandproper.platform.featureflags.config

import com.primandproper.platform.featureflags.local.InMemoryFeatureFlagManager
import com.primandproper.platform.featureflags.noop.NoopFeatureFlagManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `featureflags/config/config_test.go` (validation + provider selection). */
class FeatureFlagsConfigTest {
    @Test
    fun `validate accepts launchdarkly with config`() {
        FeatureFlagsConfig(
            provider = FeatureFlagProvider.LAUNCH_DARKLY,
            launchDarkly = LaunchDarklyConfig(sdkKey = "key", initTimeout = 5.seconds),
        ).validate()
    }

    @Test
    fun `validate accepts a null provider for noop`() {
        FeatureFlagsConfig(provider = null).validate()
    }

    @Test
    fun `fromValue resolves known providers and returns null for unknown or blank`() {
        assertEquals(FeatureFlagProvider.LAUNCH_DARKLY, FeatureFlagProvider.fromValue("  LAUNCHDARKLY  "))
        assertEquals(FeatureFlagProvider.POSTHOG, FeatureFlagProvider.fromValue("posthog"))
        assertNull(FeatureFlagProvider.fromValue("invalid_provider"))
        assertNull(FeatureFlagProvider.fromValue(""))
    }

    @Test
    fun `validate accepts posthog with config`() {
        FeatureFlagsConfig(
            provider = FeatureFlagProvider.POSTHOG,
            postHog = PostHogConfig(projectApiKey = "p", personalApiKey = "k"),
        ).validate()
    }

    @Test
    fun `validate rejects launchdarkly missing config`() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = FeatureFlagProvider.LAUNCH_DARKLY).validate()
        }
    }

    @Test
    fun `validate rejects posthog missing config`() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = FeatureFlagProvider.POSTHOG).validate()
        }
    }

    @Test
    fun `provide returns noop for a null provider`() {
        val ffm = FeatureFlagsConfig(provider = null).FeatureFlagManager()
        assertIs<NoopFeatureFlagManager>(ffm)
    }

    @Test
    fun `provide throws for a known provider whose backend was not wired`() {
        // launchdarkly is a valid provider, but with no backend registered it must fail loudly rather
        // than hand back noop flags.
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = FeatureFlagProvider.LAUNCH_DARKLY).FeatureFlagManager()
        }
    }

    @Test
    fun `provide dispatches to the registered backend`() {
        var called = false
        val ffm =
            FeatureFlagsConfig(provider = FeatureFlagProvider.LAUNCH_DARKLY).FeatureFlagManager(
                backends =
                    mapOf(
                        FeatureFlagProvider.LAUNCH_DARKLY to { _ ->
                            called = true
                            InMemoryFeatureFlagManager()
                        },
                    ),
            )
        assertTrue(called)
        assertIs<InMemoryFeatureFlagManager>(ffm)
    }
}
