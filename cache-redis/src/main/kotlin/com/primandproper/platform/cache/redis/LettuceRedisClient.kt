package com.primandproper.platform.cache.redis

import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.lettuce.core.RedisClient as LettuceClient
import java.time.Duration as JavaDuration

/**
 * The production [RedisClient], adapting Lettuce's async command API. Each Lettuce call returns a
 * `RedisFuture` (a `CompletionStage`), bridged to a coroutine with `kotlinx.coroutines.future.await()`
 * — the recommended pattern for driving Lettuce from `suspend` code.
 *
 * The connection is opened lazily on first use (under a [Mutex]) rather than in the constructor, so
 * constructing a [RedisCache] — and `provideCache` with a Redis provider — never blocks on, or
 * requires, a reachable server. This matches platform-go, whose `redis.NewClient` builds a lazily
 * connecting client.
 *
 * TODO(cluster): platform-go's `buildRedisClient` also constructs a `redis.NewClusterClient` for
 * multi-address / `Cluster` configs. Cluster support here (a `RedisClusterClient`-backed adapter)
 * is a documented seam; a single-node [LettuceRedisClient] is the primary backend. The slot-bucketing
 * in [RedisCache] is client-agnostic and already in place for when the cluster adapter lands.
 */
public class LettuceRedisClient(
    private val config: RedisCacheConfig,
) : RedisClient, AutoCloseable {
    private val mutex = Mutex()

    @Volatile
    private var client: LettuceClient? = null

    @Volatile
    private var connection: StatefulRedisConnection<String, String>? = null

    @Volatile
    private var commandsRef: RedisAsyncCommands<String, String>? = null

    private suspend fun commands(): RedisAsyncCommands<String, String> {
        commandsRef?.let { return it }
        return mutex.withLock {
            commandsRef?.let { return it }
            val address = config.queueAddresses.first()
            val (host, port) = parseAddress(address)
            val uri =
                RedisURI.Builder.redis(host, port)
                    .apply {
                        config.username?.let { withAuthentication(it, (config.password ?: "").toCharArray()) }
                            ?: config.password?.let { withPassword(it.toCharArray()) }
                    }
                    .withTimeout(JavaDuration.ofSeconds(1))
                    .build()
            val c = LettuceClient.create(uri)
            val conn = c.connect()
            val cmds = conn.async()
            client = c
            connection = conn
            commandsRef = cmds
            cmds
        }
    }

    override suspend fun get(key: String): String? = commands().get(key).await()

    override suspend fun set(
        key: String,
        value: String,
        ttlMillis: Long,
    ) {
        val cmds = commands()
        if (ttlMillis > 0) {
            cmds.set(key, value, SetArgs.Builder.px(ttlMillis)).await()
        } else {
            cmds.set(key, value).await()
        }
    }

    override suspend fun mget(keys: List<String>): List<String?> =
        commands().mget(*keys.toTypedArray()).await()
            .map { if (it.hasValue()) it.value else null }

    override suspend fun del(key: String) {
        commands().del(key).await()
    }

    override suspend fun setBatch(
        keys: List<String>,
        values: List<String>,
        ttlMillis: Long,
    ) {
        // KEYS[i] <- ARGV[i+1], all with the single millisecond TTL in ARGV[1] (non-positive = no
        // expiry). One round trip and atomic, mirroring platform-go's batchSetScript.
        val args =
            buildList {
                add(ttlMillis.toString())
                addAll(values)
            }
        commands().eval<Long>(BATCH_SET_SCRIPT, ScriptOutputType.INTEGER, keys.toTypedArray(), *args.toTypedArray()).await()
    }

    override suspend fun ping() {
        commands().ping().await()
    }

    override fun close() {
        connection?.close()
        client?.shutdown()
    }

    private fun parseAddress(address: String): Pair<String, Int> {
        val idx = address.lastIndexOf(':')
        require(idx > 0) { "invalid redis address: \"$address\" (expected host:port)" }
        val host = address.substring(0, idx)
        val port = address.substring(idx + 1).toIntOrNull() ?: error("invalid redis port in address: \"$address\"")
        return host to port
    }

    private companion object {
        const val BATCH_SET_SCRIPT = """
local ttl = tonumber(ARGV[1])
for i = 1, #KEYS do
    if ttl > 0 then
        redis.call('SET', KEYS[i], ARGV[i + 1], 'PX', ttl)
    else
        redis.call('SET', KEYS[i], ARGV[i + 1])
    end
end
return #KEYS
"""
    }
}
