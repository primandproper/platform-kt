package com.primandproper.platform.search.text.noop

import com.primandproper.platform.search.text.Index

/**
 * A no-op text [Index]: searches always return empty and writes are discarded. Port of platform-go's
 * `search/text/noop.indexManager[T]` — the safe default `provideTextIndex` falls back to for an
 * unknown or empty provider, and a convenient stand-in for tests that don't care about real indexing.
 */
public class NoopIndex<T : Any> : Index<T> {
    override suspend fun search(query: String): List<T> = emptyList()

    override suspend fun index(
        id: String,
        value: Any,
    ) {
    }

    override suspend fun delete(id: String) {
    }

    override suspend fun wipe() {
    }
}
