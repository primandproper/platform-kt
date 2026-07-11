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
    private val lock = Any()

    private val _getCalls = mutableListOf<String>()
    private val _setCalls = mutableListOf<Pair<String, T>>()
    private val _deleteCalls = mutableListOf<String>()
    private var _pingCalls = 0

    public val getCalls: List<String> get() = synchronized(lock) { _getCalls.toList() }
    public val setCalls: List<Pair<String, T>> get() = synchronized(lock) { _setCalls.toList() }
    public val deleteCalls: List<String> get() = synchronized(lock) { _deleteCalls.toList() }

    /** Number of times [ping] was called. */
    public val pingCalls: Int get() = synchronized(lock) { _pingCalls }

    override suspend fun get(key: String): T? {
        synchronized(lock) { _getCalls += key }
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        synchronized(lock) { _setCalls += key to value }
        requireFunc(setFunc, "setFunc").invoke(key, value)
    }

    override suspend fun delete(key: String) {
        synchronized(lock) { _deleteCalls += key }
        requireFunc(deleteFunc, "deleteFunc").invoke(key)
    }

    override suspend fun ping() {
        synchronized(lock) { _pingCalls++ }
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
    private val lock = Any()

    private val _getCalls = mutableListOf<String>()
    private val _setCalls = mutableListOf<Pair<String, T>>()
    private val _deleteCalls = mutableListOf<String>()
    private var _pingCalls = 0
    private val _getManyCalls = mutableListOf<List<String>>()
    private val _setManyCalls = mutableListOf<Map<String, T>>()

    public val getCalls: List<String> get() = synchronized(lock) { _getCalls.toList() }
    public val setCalls: List<Pair<String, T>> get() = synchronized(lock) { _setCalls.toList() }
    public val deleteCalls: List<String> get() = synchronized(lock) { _deleteCalls.toList() }

    /** Number of times [ping] was called. */
    public val pingCalls: Int get() = synchronized(lock) { _pingCalls }
    public val getManyCalls: List<List<String>> get() = synchronized(lock) { _getManyCalls.toList() }
    public val setManyCalls: List<Map<String, T>> get() = synchronized(lock) { _setManyCalls.toList() }

    override suspend fun get(key: String): T? {
        synchronized(lock) { _getCalls += key }
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        synchronized(lock) { _setCalls += key to value }
        requireFunc(setFunc, "setFunc").invoke(key, value)
    }

    override suspend fun delete(key: String) {
        synchronized(lock) { _deleteCalls += key }
        requireFunc(deleteFunc, "deleteFunc").invoke(key)
    }

    override suspend fun ping() {
        synchronized(lock) { _pingCalls++ }
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> {
        synchronized(lock) { _getManyCalls += keys }
        return requireFunc(getManyFunc, "getManyFunc").invoke(keys)
    }

    override suspend fun setMany(items: Map<String, T>) {
        synchronized(lock) { _setManyCalls += items }
        requireFunc(setManyFunc, "setManyFunc").invoke(items)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
