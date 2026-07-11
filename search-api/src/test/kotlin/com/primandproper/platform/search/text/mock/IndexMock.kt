package com.primandproper.platform.search.text.mock

import com.primandproper.platform.search.text.Index

/**
 * A configurable text [Index] test double, mirroring platform-go's moq-generated
 * `mocksearch.IndexMock`. Each method delegates to a settable `...Func`; calling a method whose
 * `Func` was left `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's arguments are recorded in the
 * matching `...Calls` list, standing in for moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = IndexMock<Doc>(searchFunc = { q -> listOf(Doc(q)) })
 * ```
 */
public class IndexMock<T : Any>(
    public var searchFunc: (suspend (String) -> List<T>)? = null,
    public var indexFunc: (suspend (String, T) -> Unit)? = null,
    public var deleteFunc: (suspend (String) -> Unit)? = null,
    public var wipeFunc: (suspend () -> Unit)? = null,
) : Index<T> {
    private val lock = Any()
    private val _searchCalls = mutableListOf<String>()
    private val _indexCalls = mutableListOf<Pair<String, T>>()
    private val _deleteCalls = mutableListOf<String>()
    private var _wipeCalls = 0

    public val searchCalls: List<String> get() = synchronized(lock) { _searchCalls.toList() }
    public val indexCalls: List<Pair<String, T>> get() = synchronized(lock) { _indexCalls.toList() }
    public val deleteCalls: List<String> get() = synchronized(lock) { _deleteCalls.toList() }
    public val wipeCalls: Int get() = synchronized(lock) { _wipeCalls }

    override suspend fun search(query: String): List<T> {
        synchronized(lock) { _searchCalls += query }
        return requireFunc(searchFunc, "searchFunc").invoke(query)
    }

    override suspend fun index(
        id: String,
        value: T,
    ) {
        synchronized(lock) { _indexCalls += id to value }
        requireFunc(indexFunc, "indexFunc").invoke(id, value)
    }

    override suspend fun delete(id: String) {
        synchronized(lock) { _deleteCalls += id }
        requireFunc(deleteFunc, "deleteFunc").invoke(id)
    }

    override suspend fun wipe() {
        synchronized(lock) { _wipeCalls++ }
        requireFunc(wipeFunc, "wipeFunc").invoke()
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
