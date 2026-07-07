package com.primandproper.platform.search.vector.pgvector

/**
 * Turns a vector's metadata payload into the `jsonb` text stored alongside it, and back. Serialization
 * stays at the boundary, exactly as in platform-go, whose pgvector backend json-marshals `*T` into the
 * metadata column and json-unmarshals it back (`marshalMetadata`/`unmarshalMetadata`).
 *
 * As with `cache.CacheCodec`, the codec is injected because Kotlin has no universal, reflection-free
 * JSON encoder on the pure-JVM classpath. Following Go's contract: [encode] renders a `null` payload
 * as the empty object `{}` (so the NOT NULL column constraint holds), and [decode] returns `null` for
 * an absent/empty payload so callers can distinguish "no metadata" from a populated value.
 */
public interface MetadataCodec<T : Any> {
    /** Encodes [metadata] into its stored JSON form; a `null` payload renders as `{}`. */
    public fun encode(metadata: T?): String

    /** Decodes stored JSON back into a `T`, or `null` for an absent/empty payload. */
    public fun decode(json: String?): T?
}

/**
 * A pass-through [MetadataCodec] for `String` payloads that already hold JSON text (or a raw string
 * payload in tests). Stores the value verbatim, substituting `{}` for `null`, and treats an
 * absent/blank/`null`/`{}` payload as "no metadata".
 */
public object StringMetadataCodec : MetadataCodec<String> {
    override fun encode(metadata: String?): String = metadata ?: "{}"

    override fun decode(json: String?): String? = if (json.isNullOrBlank() || json == "null" || json == "{}") null else json
}
