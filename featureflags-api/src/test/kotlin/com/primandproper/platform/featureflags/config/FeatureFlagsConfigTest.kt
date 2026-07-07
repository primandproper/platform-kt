package com.primandproper.platform.featureflags.config

import com.primandproper.platform.featureflags.local.InMemoryFeatureFlagManager
import com.primandproper.platform.featureflags.noop.NoopFeatureFlagManager
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `featureflags/config/config_test.go` (validation + provider selection). */
class FeatureFlagsConfigTest {
    @Test
    fun `validate accepts launchdarkly with config`() {
        FeatureFlagsConfig(
            provider = PROVIDER_LAUNCH_DARKLY,
            launchDarkly = LaunchDarklyConfig(sdkKey = "key", initTimeout = 5.seconds),
        ).validate()
    }

    @Test
    fun `validate accepts empty provider for noop`() {
        FeatureFlagsConfig(provider = "").validate()
    }

    @Test
    fun `validate rejects invalid provider`() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = "invalid_provider").validate()
        }
    }

    @Test
    fun `validate accepts posthog with config`() {
        FeatureFlagsConfig(
            provider = PROVIDER_POSTHOG,
            postHog = PostHogConfig(projectApiKey = "p", personalApiKey = "k"),
        ).validate()
    }

    @Test
    fun `validate rejects launchdarkly missing config`() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = PROVIDER_LAUNCH_DARKLY).validate()
        }
    }

    @Test
    fun `validate rejects posthog missing config`() {
        assertFailsWith<IllegalArgumentException> {
            FeatureFlagsConfig(provider = PROVIDER_POSTHOG).validate()
        }
    }

    @Test
    fun `provide returns noop for empty provider`() {
        val ffm = FeatureFlagsConfig(provider = "").provideFeatureFlagManager()
        assertIs<NoopFeatureFlagManager>(ffm)
    }

    @Test
    fun `provide returns noop for unknown provider`() {
        val ffm = FeatureFlagsConfig(provider = "something_unknown").provideFeatureFlagManager()
        assertIs<NoopFeatureFlagManager>(ffm)
    }

    @Test
    fun `provide normalizes whitespace and case before backend lookup`() {
        var called = false
        val ffm =
            FeatureFlagsConfig(provider = "  LAUNCHDARKLY  ").provideFeatureFlagManager(
                backends =
                    mapOf(
                        PROVIDER_LAUNCH_DARKLY to { _ ->
                            called = true
                            InMemoryFeatureFlagManager()
                        },
                    ),
            )
        assertTrue(called)
        assertIs<InMemoryFeatureFlagManager>(ffm)
    }
}
