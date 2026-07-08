package com.primandproper.platform.featureflags.config

import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.noop.NoopFeatureFlagManager
import kotlin.time.Duration

/** Provider identifier for the LaunchDarkly backend. */
public const val PROVIDER_LAUNCH_DARKLY: String = "launchdarkly"

/** Provider identifier for the PostHog backend. */
public const val PROVIDER_POSTHOG: String = "posthog"

/**
 * Settings for the LaunchDarkly backend. Owned by the api module (rather than the
 * `:featureflags-launchdarkly` module) so [FeatureFlagsConfig] can validate it without depending on
 * the vendor SDK; the LaunchDarkly module consumes this DTO to build its client. Port of
 * platform-go's `featureflags/launchdarkly.Config`.
 */
public data class LaunchDarklyConfig(
    public val sdkKey: String = "",
    public val initTimeout: Duration = Duration.ZERO,
)

/**
 * Settings for the PostHog backend. TODO(posthog): the PostHog server backend is a documented seam;
 * only these config fields are ported. Port of platform-go's `featureflags/posthog.Config`.
 */
public data class PostHogConfig(
    public val projectApiKey: String = "",
    public val personalApiKey: String = "",
)

/**
 * Configures which feature flag backend to build. Port of platform-go's `featureflagscfg.Config`,
 * trimmed to the fields that make sense on a client (the server-only circuit-breaker wiring is left
 * to the backend modules).
 */
public data class FeatureFlagsConfig(
    public val provider: String = "",
    public val launchDarkly: LaunchDarklyConfig? = null,
    public val postHog: PostHogConfig? = null,
) {
    /**
     * Validates the config, mirroring platform-go's `ValidateWithContext`: the provider must be one
     * of the known values (or empty for noop), and the matching sub-config must be present when a
     * provider is selected.
     *
     * @throws IllegalArgumentException when the config is invalid.
     */
    public fun validate() {
        require(provider in setOf(PROVIDER_LAUNCH_DARKLY, PROVIDER_POSTHOG, "")) {
            "invalid feature flag provider: $provider"
        }
        if (provider == PROVIDER_LAUNCH_DARKLY) {
            requireNotNull(launchDarkly) { "launchDarkly config is required when provider is $PROVIDER_LAUNCH_DARKLY" }
        }
        if (provider == PROVIDER_POSTHOG) {
            requireNotNull(postHog) { "postHog config is required when provider is $PROVIDER_POSTHOG" }
        }
    }

    /**
     * Builds a [FeatureFlagManager] from this config. An empty or unrecognized provider yields a
     * [NoopFeatureFlagManager] — the same default platform-go falls back to. The vendor backends
     * (LaunchDarkly, PostHog) live in their own modules; wire them by passing a factory keyed by
     * provider name, so this module never depends on a vendor SDK.
     *
     * The provider string is normalized (trimmed, lowercased) before lookup, mirroring the Go
     * `strings.TrimSpace(strings.ToLower(...))` switch.
     */
    public fun provideFeatureFlagManager(
        backends: Map<String, (FeatureFlagsConfig) -> FeatureFlagManager> = emptyMap(),
    ): FeatureFlagManager {
        val key = provider.trim().lowercase()
        val factory = backends[key]
        return factory?.invoke(this) ?: NoopFeatureFlagManager()
    }
}
