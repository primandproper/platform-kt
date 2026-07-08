package com.primandproper.platform.ratelimiting.redis

/**
 * An in-memory [RedisClient] fake, standing in for a live server so [RedisRateLimiter] mapping logic
 * can be unit-tested — the analog of Go's `mockRedisClient` in `redis_test.go`. Records every EVAL so
 * tests can assert the script, keys, and ARGV (limit, window, unique member).
 *
 * @param result the integer the script "returns" (`1` = allowed, `0` = throttled).
 * @param failOnEval when set, [evalInt] throws instead of returning, exercising the error path.
 */
class FakeRedisClient(
    var result: Long = 1L,
    var failOnEval: Boolean = false,
) : RedisClient {
    data class EvalCall(
        val script: String,
        val keys: List<String>,
        val args: List<Any>,
    )

    val evalCalls: MutableList<EvalCall> = mutableListOf()
    var closeCalls: Int = 0
        private set
    var closeError: RuntimeException? = null

    override suspend fun evalInt(
        script: String,
        keys: List<String>,
        args: List<Any>,
    ): Long {
        evalCalls += EvalCall(script, keys, args)
        if (failOnEval) throw RuntimeException("injected eval failure")
        return result
    }

    override fun close() {
        closeCalls++
        closeError?.let { throw it }
    }
}
