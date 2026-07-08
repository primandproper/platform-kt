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
    public var indexFunc: (suspend (String, Any) -> Unit)? = null,
    public var deleteFunc: (suspend (String) -> Unit)? = null,
    public var wipeFunc: (suspend () -> Unit)? = null,
) : Index<T> {
    public val searchCalls: MutableList<String> = mutableListOf()
    public val indexCalls: MutableList<Pair<String, Any>> = mutableListOf()
    public val deleteCalls: MutableList<String> = mutableListOf()
    public val wipeCalls: MutableList<Unit> = mutableListOf()

    override suspend fun search(query: String): List<T> {
        searchCalls += query
        return requireFunc(searchFunc, "searchFunc").invoke(query)
    }

    override suspend fun index(
        id: String,
        value: Any,
    ) {
        indexCalls += id to value
        requireFunc(indexFunc, "indexFunc").invoke(id, value)
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        requireFunc(deleteFunc, "deleteFunc").invoke(id)
    }

    override suspend fun wipe() {
        wipeCalls += Unit
        requireFunc(wipeFunc, "wipeFunc").invoke()
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
