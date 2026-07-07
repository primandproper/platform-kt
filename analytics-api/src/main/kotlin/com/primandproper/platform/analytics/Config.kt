package com.primandproper.platform.analytics

/**
 * Known analytics providers. Ports platform-go's `analyticscfg.ProviderSegment` / `ProviderPostHog`.
 */
public object Provider {
    public const val SEGMENT: String = "segment"
    public const val POSTHOG: String = "posthog"
}

/** Set of recognized providers, used by [SourceConfig.validate]. */
private val KNOWN_PROVIDERS = setOf(Provider.SEGMENT, Provider.POSTHOG)

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
    val provider: String = "",
    val segment: SegmentConfig? = null,
    val posthog: PostHogConfig? = null,
) {
    /**
     * Validates that the provider is known and its matching credentials block is present, so a source
     * with no provider/key fails fast rather than silently degrading to a noop at runtime. Mirrors
     * `SourceConfig.ValidateWithContext`. Returns a human-readable reason on failure, or `null` when
     * valid.
     */
    public fun validate(): String? {
        val p = provider.trim().lowercase()
        if (p !in KNOWN_PROVIDERS) {
            return "provider must be one of ${KNOWN_PROVIDERS.sorted()}, was \"$provider\""
        }
        if (p == Provider.SEGMENT && (segment == null || segment.apiToken.isBlank())) {
            return "segment provider requires a non-empty segment API token"
        }
        if (p == Provider.POSTHOG && (posthog == null || posthog.apiKey.isBlank())) {
            return "posthog provider requires a non-empty posthog API key"
        }
        return null
    }

    /** Whether this source passes [validate]. */
    public val isValid: Boolean get() = validate() == null
}

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
