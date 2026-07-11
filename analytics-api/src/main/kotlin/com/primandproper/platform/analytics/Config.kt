package com.primandproper.platform.analytics

/**
 * Known analytics providers. Ports platform-go's `analyticscfg.ProviderSegment` / `ProviderPostHog`,
 * modelled as an enum so an unknown provider is rejected the way Go's `validation.In(...)` rejects it.
 * [value] is the wire/string form validated against configuration.
 */
public enum class AnalyticsProvider(
    public val value: String,
) {
    SEGMENT("segment"),
    POSTHOG("posthog"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — the parse edge where a raw config string becomes the typed enum.
         * Mirrors Go's `validation.In(ProviderSegment, ProviderPostHog)` rejecting an unknown name.
         */
        public fun fromValue(value: String): AnalyticsProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/** Segment credentials. Port of platform-go's `segment.Config`. */
public data class SegmentConfig(
    val apiToken: String = "",
)

/**
 * PostHog credentials. Port of platform-go's `posthog.Config`. PostHog itself is a documented
 * `TODO(posthog)` backend seam (see `:analytics-segment`), but its config shape is carried so a
 * source can be declared and validated ahead of the backend wiring.
 */
public data class PostHogConfig(
    val apiKey: String = "",
    val endpoint: String = "",
)

/**
 * Per-source analytics config: a provider plus the matching credentials block. Port of
 * platform-go's `analyticscfg.SourceConfig` (the circuit-breaker sub-config is deferred to the
 * backend wiring layer, since `:circuitbreaking` is injected at the backend, not declared here).
 */
public data class SourceConfig(
    val provider: AnalyticsProvider,
    val segment: SegmentConfig? = null,
    val posthog: PostHogConfig? = null,
) {
    /**
     * Validates that the [provider]'s matching credentials block is present, so a source with no key
     * fails fast rather than silently degrading to a noop at runtime. The provider itself is already a
     * typed [AnalyticsProvider] (resolved at the parse edge), so the `In(...)` check Go performs is
     * enforced by the type; only the "provider requires its credentials block" rule remains, over an
     * exhaustive `when`. Mirrors `SourceConfig.ValidateWithContext`, and matches the throwing
     * `validate()` convention every other platform config uses. Throws
     * [InvalidAnalyticsSourceConfigException] on failure.
     */
    public fun validate() {
        when (provider) {
            AnalyticsProvider.SEGMENT ->
                if (segment == null || segment.apiToken.isBlank()) {
                    throw InvalidAnalyticsSourceConfigException("segment provider requires a non-empty segment API token")
                }
            AnalyticsProvider.POSTHOG ->
                if (posthog == null || posthog.apiKey.isBlank()) {
                    throw InvalidAnalyticsSourceConfigException("posthog provider requires a non-empty posthog API key")
                }
        }
    }
}

/** Thrown when [SourceConfig.validate] finds a missing credentials block for the selected provider. */
public class InvalidAnalyticsSourceConfigException(
    reason: String,
) : IllegalArgumentException(reason)

/**
 * Per-source analytics config for the analytics proxy. The sources are codified (`ios` and `web`),
 * matching platform-go's `analyticscfg.ProxySourcesConfig`.
 */
public data class ProxySourcesConfig(
    val ios: SourceConfig? = null,
    val web: SourceConfig? = null,
) {
    /**
     * Returns a source-name -> config map for the multisource reporter, skipping nil entries.
     * Mirrors `ProxySourcesConfig.ToMap`.
     */
    public fun toMap(): Map<String, SourceConfig> =
        buildMap {
            ios?.let { put("ios", it) }
            web?.let { put("web", it) }
        }
}
