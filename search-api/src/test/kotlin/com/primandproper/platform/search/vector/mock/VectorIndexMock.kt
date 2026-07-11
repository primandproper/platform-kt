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
    private val lock = Any()
    private val _upsertCalls = mutableListOf<List<Vector<T>>>()
    private val _deleteCalls = mutableListOf<List<String>>()
    private var _wipeCalls = 0
    private val _queryCalls = mutableListOf<QueryRequest>()

    public val upsertCalls: List<List<Vector<T>>> get() = synchronized(lock) { _upsertCalls.toList() }
    public val deleteCalls: List<List<String>> get() = synchronized(lock) { _deleteCalls.toList() }
    public val wipeCalls: Int get() = synchronized(lock) { _wipeCalls }
    public val queryCalls: List<QueryRequest> get() = synchronized(lock) { _queryCalls.toList() }

    override suspend fun upsert(vararg vectors: Vector<T>) {
        val asList = vectors.toList()
        synchronized(lock) { _upsertCalls += asList }
        requireFunc(upsertFunc, "upsertFunc").invoke(asList)
    }

    override suspend fun delete(vararg ids: String) {
        val asList = ids.toList()
        synchronized(lock) { _deleteCalls += asList }
        requireFunc(deleteFunc, "deleteFunc").invoke(asList)
    }

    override suspend fun wipe() {
        synchronized(lock) { _wipeCalls++ }
        requireFunc(wipeFunc, "wipeFunc").invoke()
    }

    override suspend fun query(request: QueryRequest): List<QueryResult<T>> {
        synchronized(lock) { _queryCalls += request }
        return requireFunc(queryFunc, "queryFunc").invoke(request)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
