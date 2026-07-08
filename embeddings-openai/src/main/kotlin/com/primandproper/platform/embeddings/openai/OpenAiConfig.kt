package com.primandproper.platform.embeddings.openai

/**
 * Configures the OpenAI embeddings backend. Port of platform-go's `openai.Config`.
 *
 * Go's only validated field is the API key (`validation.Required`), mirrored here by [validate] (and
 * by the [EmptyApiKeyException] the [OpenAiEmbedder] factory throws on an empty key). [baseUrl] and
 * [defaultModel] are optional overrides that fall back to OpenAI's real host and
 * `text-embedding-3-small` respectively — the same defaults Go applies inside `GenerateEmbedding`.
 *
 * Go's `Config` also carries a `Timeout` that bounds its `http.Client`; this port omits it because
 * the transport is the injected [com.primandproper.platform.httpclient.HttpClient], whose timeout is
 * an `HttpClientConfig` concern — the same simplification `:email-resend`'s `ResendConfig` makes.
 */
public data class OpenAiConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val defaultModel: String = "",
) {
    /** Throws [EmptyApiKeyException] when [apiKey] is empty, mirroring Go's `Required` rule. */
    public fun validate() {
        if (apiKey.isEmpty()) throw EmptyApiKeyException()
    }

    public companion object {
        /** OpenAI's production API base. Requests target `<baseUrl>/v1/embeddings`. Mirrors Go's `defaultBaseURL`. */
        public const val DEFAULT_BASE_URL: String = "https://api.openai.com"

        /** The model used when neither the input nor the config names one. Mirrors Go's `defaultModel`. */
        public const val DEFAULT_MODEL: String = "text-embedding-3-small"
    }
}
