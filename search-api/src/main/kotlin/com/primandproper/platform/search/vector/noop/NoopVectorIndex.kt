package com.primandproper.platform.search.vector.noop

import com.primandproper.platform.search.vector.Index
import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.QueryResult
import com.primandproper.platform.search.vector.Vector

/**
 * A no-op vector [Index]: queries return an empty result set and writes silently succeed. Port of
 * platform-go's `search/vector/noop.indexManager[T]` — the safe default `provideVectorIndex` falls
 * back to for an unknown or empty provider.
 */
public class NoopVectorIndex<T : Any> : Index<T> {
    override suspend fun upsert(vararg vectors: Vector<T>) {
    }

    override suspend fun delete(vararg ids: String) {
    }

    override suspend fun wipe() {
    }

    override suspend fun query(request: QueryRequest): List<QueryResult<T>> = emptyList()
}
