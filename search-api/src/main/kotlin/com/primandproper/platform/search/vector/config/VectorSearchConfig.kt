package com.primandproper.platform.search.vector.config

/**
 * The supported vector-search providers. Port of platform-go's `vectorsearchcfg.PGvectorProvider` /
 * `QdrantProvider` constants. [value] is the wire/string form validated against configuration.
 */
public enum class VectorSearchProvider(
    public val value: String,
) {
    PGVECTOR("pgvector"),
    QDRANT("qdrant"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(PGvectorProvider, QdrantProvider)`
         * and the same canonicalization `ProvideIndex` dispatches on.
         */
        public fun fromValue(value: String): VectorSearchProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider-agnostic vector-search configuration. Port of the portable part of platform-go's
 * `vectorsearchcfg.Config`: the chosen [provider].
 *
 * The backend connection/schema settings (`pgvector.Config`, `qdrant.Config`) and the circuit-breaker
 * settings live with each backend module, so this API module stays vendor-free — the
 * `provideVectorIndex` factory that unites them lives in `:search-pgvector`.
 */
public data class VectorSearchConfig(
    val provider: VectorSearchProvider,
)
