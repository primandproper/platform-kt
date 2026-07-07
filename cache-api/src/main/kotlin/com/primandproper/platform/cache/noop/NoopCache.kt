package com.primandproper.platform.cache.noop

import com.primandproper.platform.cache.BatchCache

/**
 * A no-op [BatchCache]: reads always miss and writes are discarded. Port of platform-go's
 * `cache/noop.Cache[T]` — a safe default for wiring, and for tests that don't care about real caching.
 *
 * Go's `Get` returns `cache.ErrNotFound`; the idiomatic miss signal in this port is a `null` return,
 * so [get] returns `null` and [getMany] returns an empty map.
 */
public class NoopCache<T : Any> : BatchCache<T> {
    override suspend fun get(key: String): T? = null

    override suspend fun set(
        key: String,
        value: T,
    ) {
    }

    override suspend fun delete(key: String) {
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> = emptyMap()

    override suspend fun setMany(items: Map<String, T>) {
    }

    override suspend fun ping() {
    }
}
