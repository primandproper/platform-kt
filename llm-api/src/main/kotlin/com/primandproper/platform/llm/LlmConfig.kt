package com.primandproper.platform.llm

/**
 * The supported LLM providers. Port of platform-go's `llmcfg.Provider*` constants. [value] is the
 * wire/string form validated against configuration.
 *
 * Only [ANTHROPIC] is implemented in this port (`:llm-anthropic`); [OPENAI] is a documented
 * `TODO(openai)` seam — the enum still lists it so a config naming `"openai"` validates as a known
 * provider even before its backend lands, matching Go's `validation.In(ProviderOpenAI,
 * ProviderAnthropic, "")` accepting both names.
 */
public enum class LlmProviderKind(
    public val value: String,
) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(...)` rejecting an unknown name.
         */
        public fun fromValue(value: String): LlmProviderKind? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/** Thrown when [LlmConfig.validate] is given a non-empty provider that names no known backend. */
public class InvalidLlmProviderException(
    provider: String,
) : IllegalArgumentException("unknown llm provider: $provider")

/**
 * Provider-agnostic LLM configuration. Port of the portable part of platform-go's `llmcfg.Config`:
 * the chosen [provider] selecting which backend to wire.
 *
 * The per-provider connection settings (the Anthropic/OpenAI API key, base URL, default model) live
 * with the backend modules (`AnthropicConfig` in `:llm-anthropic`), so this API module stays
 * transport-free — matching how `:email-api` keeps the Resend settings in `:email-resend`.
 * Consequently [validate] here checks only that a non-empty [provider] names a known backend; the
 * "provider X requires its config block" check that platform-go performs in `ValidateWithContext`
 * happens at the wiring layer, where the concrete per-provider config is in scope.
 *
 * An empty [provider] is permitted and selects the noop provider, mirroring Go's
 * `ProvideLLMProvider` default branch.
 */
public data class LlmConfig(
    val provider: String = "",
) {
    /** Throws [InvalidLlmProviderException] when [provider] is non-empty yet unknown. */
    public fun validate() {
        if (provider.isBlank()) return
        LlmProviderKind.fromValue(provider) ?: throw InvalidLlmProviderException(provider)
    }
}
