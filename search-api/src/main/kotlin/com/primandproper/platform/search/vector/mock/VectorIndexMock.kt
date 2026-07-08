package com.primandproper.platform.search.vector.mock

import com.primandproper.platform.search.vector.Index
import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.QueryResult
import com.primandproper.platform.search.vector.Vector

/**
 * A configurable vector [Index] test double, mirroring platform-go's moq-generated
 * `mock.IndexMock`. Each method delegates to a settable `...Func`; calling a method whose `Func` was
 * left `null` throws [IllegalStateException], the same "unmocked call surfaces immediately" behavior
 * moq's generated panic gives. Each call's arguments are recorded in the matching `...Calls` list.
 *
 * ```
 * val mock = VectorIndexMock<Doc>(queryFunc = { req -> listOf(QueryResult("a", 0.1f)) })
 * ```
 */
public class VectorIndexMock<T : Any>(
    public var upsertFunc: (suspend (List<Vector<T>>) -> Unit)? = null,
    public var deleteFunc: (suspend (List<String>) -> Unit)? = null,
    public var wipeFunc: (suspend () -> Unit)? = null,
    public var queryFunc: (suspend (QueryRequest) -> List<QueryResult<T>>)? = null,
) : Index<T> {
    public val upsertCalls: MutableList<List<Vector<T>>> = mutableListOf()
    public val deleteCalls: MutableList<List<String>> = mutableListOf()
    public val wipeCalls: MutableList<Unit> = mutableListOf()
    public val queryCalls: MutableList<QueryRequest> = mutableListOf()

    override suspend fun upsert(vararg vectors: Vector<T>) {
        val asList = vectors.toList()
        upsertCalls += asList
        requireFunc(upsertFunc, "upsertFunc").invoke(asList)
    }

    override suspend fun delete(vararg ids: String) {
        val asList = ids.toList()
        deleteCalls += asList
        requireFunc(deleteFunc, "deleteFunc").invoke(asList)
    }

    override suspend fun wipe() {
        wipeCalls += Unit
        requireFunc(wipeFunc, "wipeFunc").invoke()
    }

    override suspend fun query(request: QueryRequest): List<QueryResult<T>> {
        queryCalls += request
        return requireFunc(queryFunc, "queryFunc").invoke(request)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
