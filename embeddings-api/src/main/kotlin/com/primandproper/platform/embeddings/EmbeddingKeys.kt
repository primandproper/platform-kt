package com.primandproper.platform.embeddings

/**
 * The standard observability attribute keys for embedding generation, so a value is named identically
 * wherever it is recorded across backends. Port of the string keys platform-go's embedding backends
 * `op.Set(...)` on the operation span (`"embeddings.model"`, `"embedding.dimensions"`).
 *
 * The content length is recorded under the shared `Keys.LENGTH` from `:observability-api` (Go uses
 * `keys.LengthKey`), so it is not duplicated here.
 *
 * Note on redaction: the API key never reaches a span or log — it rides only in the outbound
 * `Authorization` header, and the backends `set` only the model/length/dimensions below. So there is
 * no key here that would carry a credential (see `:embeddings-openai`'s doc comment).
 */
public object EmbeddingKeys {
    /** The model used to generate the embedding. Mirrors Go's `"embeddings.model"`. */
    public const val MODEL: String = "embeddings.model"

    /** The dimensionality of the returned vector. Mirrors Go's `"embedding.dimensions"`. */
    public const val DIMENSIONS: String = "embedding.dimensions"
}
