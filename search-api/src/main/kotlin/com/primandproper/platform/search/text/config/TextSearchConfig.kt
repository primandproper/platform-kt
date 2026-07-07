package com.primandproper.platform.search.text.config

/**
 * The supported text-search providers. Port of platform-go's `textsearchcfg.ElasticsearchProvider` /
 * `AlgoliaProvider` constants. [value] is the wire/string form validated against configuration.
 */
public enum class TextSearchProvider(
    public val value: String,
) {
    ELASTICSEARCH("elasticsearch"),
    ALGOLIA("algolia"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(ElasticsearchProvider,
         * AlgoliaProvider)` rejecting an unknown provider name, and the same canonicalization
         * (`strings.TrimSpace(strings.ToLower(...))`) `ProvideIndex` dispatches on.
         */
        public fun fromValue(value: String): TextSearchProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider-agnostic text-search configuration. Port of the portable part of platform-go's
 * `textsearchcfg.Config`: the chosen [provider].
 *
 * The engine connection settings (`elasticsearch.Config`, `algolia.Config`) and the circuit-breaker
 * settings live with each backend module, so this API module stays vendor-free — the
 * `provideTextIndex` factory that unites them lives in `:search-elasticsearch`, mirroring how
 * platform-go's `search/text/config` package sits above the backend subpackages, and how
 * `:cache-redis` owns `provideCache`.
 */
public data class TextSearchConfig(
    val provider: TextSearchProvider,
)
