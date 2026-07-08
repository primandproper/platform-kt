package com.primandproper.platform.llm.anthropic

/**
 * The current Claude model ids, as of this port. platform-go hardcodes stale constants
 * (`claude-sonnet-4-20250514`); those are deliberately *not* copied — these are the latest models.
 */
public object AnthropicModels {
    /** Claude Opus 4.8 — the most capable model; the [AnthropicConfig.defaultModel] default. */
    public const val OPUS_4_8: String = "claude-opus-4-8"

    /** Claude Sonnet 5 — the balanced model. */
    public const val SONNET_5: String = "claude-sonnet-5"

    /** Claude Haiku 4.5 — the fastest model. */
    public const val HAIKU_4_5: String = "claude-haiku-4-5-20251001"

    /** The default model when none is configured or requested: the most capable, [OPUS_4_8]. */
    public const val DEFAULT: String = OPUS_4_8
}

/** Thrown when an empty Anthropic API key is supplied. Port of Go's `Required` rule on `Config.APIKey`. */
public class EmptyApiKeyException : IllegalArgumentException("empty Anthropic API key")

/**
 * Configures the Anthropic backend. Port of platform-go's `anthropic.Config`.
 *
 * Go validates only [apiKey] as `validation.Required`, mirrored here by [validate] (and by the
 * [EmptyApiKeyException] the [AnthropicLlmProvider] factory throws on an empty key). [baseUrl] maps to
 * Go's `BaseURL` (tests override it to point at a fake host; production keeps Anthropic's real host).
 * [defaultModel] maps to Go's `DefaultModel` but defaults to [AnthropicModels.DEFAULT] rather than
 * leaving it blank, so a provider built without an explicit model still targets the most capable
 * Claude. [maxTokens] has no analog in Go's `Config` (the any-llm library set it); the Anthropic
 * Messages API requires `max_tokens`, so it is surfaced here with a sensible default.
 */
public data class AnthropicConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val defaultModel: String = AnthropicModels.DEFAULT,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    /** Throws [EmptyApiKeyException] when [apiKey] is empty, mirroring Go's `Required` rule. */
    public fun validate() {
        if (apiKey.isEmpty()) throw EmptyApiKeyException()
    }

    public companion object {
        /** Anthropic's production API base. Requests target `<baseUrl>/v1/messages`. */
        public const val DEFAULT_BASE_URL: String = "https://api.anthropic.com"

        /** Default `max_tokens` for a completion when none is configured. */
        public const val DEFAULT_MAX_TOKENS: Int = 4096
    }
}
