package com.primandproper.platform.cache

/**
 * A generic cache. Port of platform-go's `cache.Cache[T]`.
 *
 * Go's `Get` returns `(*T, error)`, signalling a miss with the exported sentinel `cache.ErrNotFound`.
 * Kotlin has no `(value, error)` split, and returning a nullable value is the idiomatic miss signal,
 * so [get] returns `T?` where `null` means "not in the cache". The [CacheNotFoundException] sentinel
 * is still provided for callers who prefer the exception form (see [getOrThrow]).
 *
 * [T] is bound to `Any` so `T?` unambiguously distinguishes a stored value from a miss, mirroring how
 * Go stores `*T` and treats a nil pointer as absence.
 *
 * Serialization stays at the boundary, exactly as in Go: the in-memory backend holds live `T` values,
 * while a server backend (`:cache-redis`) takes a codec to turn `T` into its stored bytes.
 *
 * There is deliberately no `close()` here (unlike the platform's other lifecycle interfaces, which
 * share `SuspendCloseable`): a `Cache` does not own its backing client's lifecycle. The injected
 * `RedisClient` / store is owned by whoever constructed it, so there is nothing for the cache
 * abstraction itself to close.
 */
public interface Cache<T : Any> {
    /** Returns the value stored at [key], or `null` on a cache miss. */
    public suspend fun get(key: String): T?

    /** Stores [value] at [key], overwriting any existing value. */
    public suspend fun set(
        key: String,
        value: T,
    )

    /** Removes [key] from the cache; a no-op if it was absent. */
    public suspend fun delete(key: String)

    /** Verifies the backend is reachable, throwing if it is not. */
    public suspend fun ping()
}

/**
 * A [Cache] that also supports batched reads and writes. Not every backend supports batching, so —
 * as in Go — callers obtain a [BatchCache] via a runtime check (`cache as? BatchCache`) on a [Cache]
 * value rather than assuming it. Port of platform-go's `cache.BatchCache[T]`.
 */
public interface BatchCache<T : Any> : Cache<T> {
    /**
     * Fetches multiple keys in as few round trips as possible. Missing keys are omitted from the
     * returned map, so a key's absence from the result is a cache miss.
     */
    public suspend fun getMany(keys: List<String>): Map<String, T>

    /** Stores multiple values at once, each with the cache's configured expiration. */
    public suspend fun setMany(items: Map<String, T>)
}

/**
 * The "not found" sentinel, the analog of platform-go's exported `cache.ErrNotFound`. The idiomatic
 * miss signal in this port is a `null` return from [Cache.get]; this exception exists for callers who
 * want the throwing form via [getOrThrow], and so the sentinel remains part of the public surface.
 */
public class CacheNotFoundException : Exception("not found")

/**
 * [Cache.get] that throws [CacheNotFoundException] on a miss instead of returning `null` — the
 * throwing counterpart for call sites that treat a miss as exceptional. Mirrors the ergonomics of
 * Go's `Get` returning `cache.ErrNotFound`.
 */
public suspend fun <T : Any> Cache<T>.getOrThrow(key: String): T = get(key) ?: throw CacheNotFoundException()
