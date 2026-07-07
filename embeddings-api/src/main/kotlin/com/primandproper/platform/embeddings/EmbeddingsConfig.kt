package com.primandproper.platform.embeddings

/**
 * The supported embedding providers. Port of platform-go's `embeddingscfg.Provider*` constants.
 * [value] is the wire/string form validated against configuration.
 *
 * Only [OPENAI] is implemented in this port (`:embeddings-openai`); [OLLAMA] and [COHERE] are
 * documented `TODO(<vendor>)` seams — the enum still lists them so a config naming, say, `"ollama"`
 * validates as a known provider even before its backend lands, matching Go's `validation.In(...)`
 * accepting all three names.
 */
public enum class EmbeddingProvider(
    public val value: String,
) {
    OPENAI("openai"),
    OLLAMA("ollama"),
    COHERE("cohere"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(...)` rejecting an unknown name and
         * `ProvideEmbedder` normalizing with `strings.TrimSpace(strings.ToLower(...))`.
         */
        public fun fromValue(value: String): EmbeddingProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/** Thrown when [EmbeddingsConfig.validate] is given a non-empty provider that names no known backend. */
public class InvalidEmbeddingProviderException(
    provider: String,
) : IllegalArgumentException("unknown embeddings provider: $provider")

/**
 * Provider-agnostic embeddings configuration. Port of the portable part of platform-go's
 * `embeddingscfg.Config`: the chosen [provider].
 *
 * The per-provider connection settings (e.g. `openai.Config`'s API key, base URL, default model) live
 * with the backend modules (`OpenAiConfig` in `:embeddings-openai`), so this API module stays
 * transport-free — matching how `:cache-api` keeps the Redis settings in `:cache-redis` and
 * `:email-api` keeps `ResendConfig` in `:email-resend`. Consequently [validate] here checks only that
 * a non-empty [provider] names a known backend; the "provider X requires its config block" check that
 * platform-go performs in `ValidateWithContext` happens at the wiring layer, where the concrete
 * per-provider config is in scope.
 *
 * An empty [provider] is permitted and selects the noop embedder, mirroring Go's `ProvideEmbedder`
 * default branch (`return embeddingsnoop.NewEmbedder(), nil`).
 */
public data class EmbeddingsConfig(
    val provider: String = "",
) {
    /** Throws [InvalidEmbeddingProviderException] when [provider] is non-empty yet unknown. */
    public fun validate() {
        if (provider.isBlank()) return
        EmbeddingProvider.fromValue(provider) ?: throw InvalidEmbeddingProviderException(provider)
    }
}
