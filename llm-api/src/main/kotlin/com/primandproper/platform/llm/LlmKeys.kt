package com.primandproper.platform.llm

/**
 * The standard observability attribute keys for LLM completions, so a value is named identically
 * wherever it is recorded across backends. Port of the `"llm.*"` keys platform-go's provider
 * implementations pass to `op.Set(...)`.
 *
 * Note on redaction: the request/response *content* is never recorded on a span here (only these
 * low-cardinality metadata keys are), and a backend must never record its API key — see
 * `:llm-anthropic`, which sends the key as an `x-api-key` header the httpclient span integration
 * redacts and never attaches it as an attribute.
 */
public object LlmKeys {
    /** The model id the completion targeted. Mirrors Go's `"llm.model"`. */
    public const val MODEL: String = "llm.model"

    /** The number of messages sent in the request. Mirrors Go's `"llm.message_count"`. */
    public const val MESSAGE_COUNT: String = "llm.message_count"

    /** The total tokens (input + output) the provider reported. Mirrors Go's `"llm.tokens.total"`. */
    public const val TOTAL_TOKENS: String = "llm.tokens.total"

    /** The reason the model stopped generating. Mirrors Go's `"llm.finish_reason"`. */
    public const val FINISH_REASON: String = "llm.finish_reason"
}
