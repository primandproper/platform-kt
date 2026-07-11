package com.primandproper.platform.search.text

/**
 * Turns a document into the string form a text-search backend stores, and back. Serialization stays
 * at the boundary, exactly as in platform-go, whose Elasticsearch backend json-encodes the value
 * before indexing and json-decodes each `_source` hit back into a `*T`.
 *
 * Go can lean on `encoding/json` for any type; Kotlin has no universal, reflection-free JSON encoder
 * on the pure-JVM classpath, so — as with `cache.CacheCodec` — the codec is injected and the caller
 * chooses the mechanism (kotlinx.serialization, Jackson, Gson, …). [encode] takes `Any` so a single
 * codec can serialize a document without being re-parameterized at the backend boundary, while
 * [decode] produces the typed `T` that [IndexSearcher.search] returns.
 */
public interface DocumentCodec<T : Any> {
    /** Encodes an indexable [value] into the JSON document body sent to the backend. */
    public fun encode(value: Any): String

    /** Decodes a stored document body (a hit's `_source`) back into a `T`. */
    public fun decode(source: String): T
}

/**
 * The identity [DocumentCodec] for `String` documents — stores and returns the value verbatim. Handy
 * for tests and for indices whose documents are already serialized strings.
 */
public object StringDocumentCodec : DocumentCodec<String> {
    override fun encode(value: Any): String = value.toString()

    override fun decode(source: String): String = source
}
