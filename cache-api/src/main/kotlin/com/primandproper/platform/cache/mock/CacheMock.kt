package com.primandproper.platform.cache.mock

import com.primandproper.platform.cache.BatchCache
import com.primandproper.platform.cache.Cache

/**
 * A configurable [Cache] test double, mirroring platform-go's moq-generated `cache/mock.CacheMock`.
 * Each method delegates to a settable `...Func`; calling a method whose `Func` was left `null` throws
 * [IllegalStateException], the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's arguments are recorded in the matching `...Calls` list, standing in for
 * moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = CacheMock<String>(getFunc = { key -> "value-for-$key" })
 * ```
 */
public class CacheMock<T : Any>(
    public var getFunc: (suspend (String) -> T?)? = null,
    public var setFunc: (suspend (String, T) -> Unit)? = null,
    public var deleteFunc: (suspend (String) -> Unit)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
) : Cache<T> {
    public val getCalls: MutableList<String> = mutableListOf()
    public val setCalls: MutableList<Pair<String, T>> = mutableListOf()
    public val deleteCalls: MutableList<String> = mutableListOf()
    public val pingCalls: MutableList<Unit> = mutableListOf()

    override suspend fun get(key: String): T? {
        getCalls += key
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        setCalls += key to value
        requireFunc(setFunc, "setFunc").invoke(key, value)
    }

    override suspend fun delete(key: String) {
        deleteCalls += key
        requireFunc(deleteFunc, "deleteFunc").invoke(key)
    }

    override suspend fun ping() {
        pingCalls += Unit
        requireFunc(pingFunc, "pingFunc").invoke()
    }
}

/**
 * A configurable [BatchCache] test double, mirroring platform-go's moq-generated
 * `cache/mock.BatchCacheMock`. Follows the same "null `Func` throws, calls are recorded" contract as
 * [CacheMock], extended with the batch methods.
 */
public class BatchCacheMock<T : Any>(
    public var getFunc: (suspend (String) -> T?)? = null,
    public var setFunc: (suspend (String, T) -> Unit)? = null,
    public var deleteFunc: (suspend (String) -> Unit)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
    public var getManyFunc: (suspend (List<String>) -> Map<String, T>)? = null,
    public var setManyFunc: (suspend (Map<String, T>) -> Unit)? = null,
) : BatchCache<T> {
    public val getCalls: MutableList<String> = mutableListOf()
    public val setCalls: MutableList<Pair<String, T>> = mutableListOf()
    public val deleteCalls: MutableList<String> = mutableListOf()
    public val pingCalls: MutableList<Unit> = mutableListOf()
    public val getManyCalls: MutableList<List<String>> = mutableListOf()
    public val setManyCalls: MutableList<Map<String, T>> = mutableListOf()

    override suspend fun get(key: String): T? {
        getCalls += key
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        setCalls += key to value
        requireFunc(setFunc, "setFunc").invoke(key, value)
    }

    override suspend fun delete(key: String) {
        deleteCalls += key
        requireFunc(deleteFunc, "deleteFunc").invoke(key)
    }

    override suspend fun ping() {
        pingCalls += Unit
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> {
        getManyCalls += keys
        return requireFunc(getManyFunc, "getManyFunc").invoke(keys)
    }

    override suspend fun setMany(items: Map<String, T>) {
        setManyCalls += items
        requireFunc(setManyFunc, "setManyFunc").invoke(items)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
