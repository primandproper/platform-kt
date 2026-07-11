package com.primandproper.platform.distributedlock.redis

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
 * The production [RedisLockClient], adapting Lettuce's async command API. Each Lettuce call returns a
 * `RedisFuture` (a `CompletionStage`), bridged to a coroutine with `kotlinx.coroutines.future.await()`
 * — the recommended pattern for driving Lettuce from `suspend` code.
 *
 * The connection is opened lazily on first use (under a [Mutex]) rather than in the constructor, so
 * constructing a [RedisLocker] never blocks on, or requires, a reachable server. This matches
 * platform-go, whose `redis.NewClient` builds a lazily connecting client.
 *
 * TODO(cluster): platform-go's `buildRedisClient` also constructs a `redis.NewClusterClient` for
 * multi-address configs. Cluster support here (a `RedisClusterClient`-backed adapter) is a documented
 * seam; a single-node client is the primary backend. The compare-and-act release/refresh scripts each
 * touch a single key, so they are already cluster-safe once the adapter lands.
 */
public class LettuceRedisLockClient(
    private val config: RedisLockConfig,
) : RedisLockClient {
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
            val (host, port) = parseAddress(config.addresses.first())
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

    override suspend fun setNx(
        key: String,
        value: String,
        ttlMillis: Long,
    ): Boolean {
        // A sub-millisecond TTL truncates to 0. A `SET NX` without `PX` is a *permanent* lock — a
        // deadlock if the holder ever crashes — so reject it loudly rather than silently omitting the
        // expiry. Callers should already have been stopped by RedisLocker's TTL guard; this is the
        // transport-boundary backstop.
        require(ttlMillis >= 1) { "redis lock TTL must be at least 1ms, got ${ttlMillis}ms" }
        val args = SetArgs.Builder.nx().px(ttlMillis)
        // Lettuce returns "OK" when the write happened and null when NX rejected it (key present).
        return commands().set(key, value, args).await() == "OK"
    }

    override suspend fun eval(
        script: String,
        keys: List<String>,
        args: List<String>,
    ): Long = commands().eval<Long>(script, ScriptOutputType.INTEGER, keys.toTypedArray(), *args.toTypedArray()).await()

    override suspend fun ping() {
        commands().ping().await()
    }

    override suspend fun close() {
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
}
