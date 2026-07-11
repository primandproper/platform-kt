package com.primandproper.platform.featureflags.config

import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.noop.NoopFeatureFlagManager
import kotlin.time.Duration

/**
 * The supported feature-flag providers. Port of platform-go's `featureflagscfg` provider constants,
 * modelled as an enum so an unknown provider is rejected the way Go's validation rejects it. [value] is
 * the wire/string form validated against configuration.
 *
 * The vendor backends (LaunchDarkly, PostHog) live in their own modules; the enum lists them so a
 * config validates before the backend is wired. An absent provider (the [FeatureFlagsConfig.provider]
 * `null`) selects the noop manager.
 */
public enum class FeatureFlagProvider(
    public val value: String,
) {
    LAUNCH_DARKLY("launchdarkly"),
    POSTHOG("posthog"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — the parse edge where a raw config string becomes the typed enum.
         * A blank value resolves to `null`, which [FeatureFlagsConfig] treats as the noop default.
         */
        public fun fromValue(value: String): FeatureFlagProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

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
 *
 * [provider] is a typed [FeatureFlagProvider], resolved from its string form once at the parse edge
 * ([FeatureFlagProvider.fromValue]); a `null` provider (the default) is the explicit opt-in for the
 * noop manager, mirroring Go's empty-provider fallback.
 */
public data class FeatureFlagsConfig(
    public val provider: FeatureFlagProvider? = null,
    public val launchDarkly: LaunchDarklyConfig? = null,
    public val postHog: PostHogConfig? = null,
) {
    /**
     * Validates the config, mirroring platform-go's `ValidateWithContext`: the matching sub-config must
     * be present when a provider is selected. The provider itself is already a typed
     * [FeatureFlagProvider] (the `In(...)` check Go performs is enforced by the type), so validation is
     * an exhaustive `when` — a `null` provider (noop) needs no sub-config.
     *
     * @throws IllegalArgumentException when the config is invalid.
     */
    public fun validate() {
        when (provider) {
            null -> Unit
            FeatureFlagProvider.LAUNCH_DARKLY ->
                requireNotNull(launchDarkly) { "launchDarkly config is required when provider is ${provider.value}" }
            FeatureFlagProvider.POSTHOG ->
                requireNotNull(postHog) { "postHog config is required when provider is ${provider.value}" }
        }
    }

    /**
     * Builds a [FeatureFlagManager] from this config. A `null` [provider] is the explicit opt-in for a
     * [NoopFeatureFlagManager] — the same default platform-go falls back to. Any other provider must
     * resolve to a registered backend: the vendor backends (LaunchDarkly, PostHog) live in their own
     * modules; wire them by passing a factory keyed by [FeatureFlagProvider], so this module never
     * depends on a vendor SDK.
     *
     * A known provider whose backend was never wired into [backends] fails loudly rather than silently
     * degrading to noop flags, mirroring the loud-failure pattern of `SecretsConfig.SecretSource`.
     *
     * @throws IllegalArgumentException when [provider] is non-null and has no registered backend.
     */
    public fun FeatureFlagManager(
        backends: Map<FeatureFlagProvider, (FeatureFlagsConfig) -> FeatureFlagManager> = emptyMap(),
    ): FeatureFlagManager {
        val selected = provider ?: return NoopFeatureFlagManager
        val factory =
            backends[selected]
                ?: throw IllegalArgumentException("unknown or unwired feature flag provider: \"${selected.value}\"")
        return factory.invoke(this)
    }
}
