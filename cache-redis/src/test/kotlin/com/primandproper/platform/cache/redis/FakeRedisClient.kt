package com.primandproper.platform.cache.redis

/**
 * An in-memory [RedisClient] fake, standing in for a live server so [RedisCache] mapping logic can be
 * unit-tested — the analog of Go's `redisclient_mock_test.go`. Records the batch calls it received so
 * tests can assert slot bucketing.
 */
class FakeRedisClient(
    var failOn: String? = null,
) : RedisClient {
    val store: MutableMap<String, String> = linkedMapOf()
    val getCalls: MutableList<String> = mutableListOf()
    val mgetCalls: MutableList<List<String>> = mutableListOf()
    val setBatchCalls: MutableList<List<String>> = mutableListOf()

    private fun maybeFail(op: String) {
        if (failOn == op) throw RuntimeException("injected $op failure")
    }

    override suspend fun get(key: String): String? {
        getCalls += key
        maybeFail("get")
        return store[key]
    }

    override suspend fun set(
        key: String,
        value: String,
        ttlMillis: Long,
    ) {
        maybeFail("set")
        store[key] = value
    }

    override suspend fun mget(keys: List<String>): List<String?> {
        maybeFail("mget")
        mgetCalls += keys
        return keys.map { store[it] }
    }

    override suspend fun del(key: String) {
        maybeFail("del")
        store.remove(key)
    }

    override suspend fun setBatch(
        keys: List<String>,
        values: List<String>,
        ttlMillis: Long,
    ) {
        maybeFail("setBatch")
        setBatchCalls += keys
        keys.forEachIndexed { idx, key -> store[key] = values[idx] }
    }

    override suspend fun ping() {
        maybeFail("ping")
    }
}
