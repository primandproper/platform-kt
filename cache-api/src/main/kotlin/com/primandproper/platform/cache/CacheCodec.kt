package com.primandproper.platform.cache

/**
 * Turns a cached value into the string form a serializing backend stores, and back. Serialization
 * stays at the boundary, exactly as in platform-go, whose `redisCacheImpl` gob-encodes `*T` into a
 * stored string (`encode`/`decode`). Go can rely on `encoding/gob` for any type; Kotlin has no
 * universal binary encoder, so the codec is injected — the caller chooses JSON, protobuf,
 * java-serialization, or a trivial identity codec for `String` values.
 *
 * The in-memory backend ([InMemoryCache]) holds live `T` values and needs no codec; the serializing
 * backends (`:cache-redis` and `:cache-android`) require one.
 */
public interface CacheCodec<T : Any> {
    /** Encodes [value] into its stored string form. */
    public fun encode(value: T): String

    /** Decodes a stored string back into a `T`. */
    public fun decode(encoded: String): T
}

/** The identity [CacheCodec] for `String` values — stores the value verbatim. Handy for tests and for caches of raw strings. */
public object StringCacheCodec : CacheCodec<String> {
    override fun encode(value: String): String = value

    override fun decode(encoded: String): String = encoded
}
