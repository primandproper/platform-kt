package com.primandproper.platform.distributedlock.redis

/**
 * An in-memory [RedisLockClient] fake, standing in for a live server so [RedisLocker] logic can be
 * unit-tested — the analog of Go's hand-written `fakeRedisClient` in `distributedlock/redis`. Each
 * command kind has a configurable result and error; call counts and last-seen arguments are recorded
 * for assertions.
 */
class FakeRedisLockClient(
    var setNxResult: Boolean = false,
    var evalResult: Long = 0L,
    var setNxErr: Throwable? = null,
    var evalErr: Throwable? = null,
    var pingErr: Throwable? = null,
    var closeErr: Throwable? = null,
) : RedisLockClient {
    var setNxCalls: Int = 0
    var evalCalls: Int = 0
    var pingCalls: Int = 0
    var closeCalls: Int = 0
    var lastSetKey: String? = null
    var lastSetValue: String? = null
    var lastSetTtl: Long = 0L
    var lastEvalKey: String? = null
    var lastEvalArgs: List<String> = emptyList()

    override suspend fun setNx(
        key: String,
        value: String,
        ttlMillis: Long,
    ): Boolean {
        setNxCalls++
        lastSetKey = key
        lastSetValue = value
        lastSetTtl = ttlMillis
        setNxErr?.let { throw it }
        return setNxResult
    }

    override suspend fun eval(
        script: String,
        keys: List<String>,
        args: List<String>,
    ): Long {
        evalCalls++
        lastEvalKey = keys.firstOrNull()
        lastEvalArgs = args
        evalErr?.let { throw it }
        return evalResult
    }

    override suspend fun ping() {
        pingCalls++
        pingErr?.let { throw it }
    }

    override suspend fun close() {
        closeCalls++
        closeErr?.let { throw it }
    }
}
