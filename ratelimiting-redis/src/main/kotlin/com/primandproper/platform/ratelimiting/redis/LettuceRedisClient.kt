package com.primandproper.platform.ratelimiting.redis

import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.lettuce.core.RedisClient as LettuceClient
import java.time.Duration as JavaDuration

/**
 * The production [RedisClient], adapting Lettuce's async command API. The `EVAL` call returns a
 * `RedisFuture` (a `CompletionStage`), bridged to a coroutine with `kotlinx.coroutines.future.await()`
 * — the recommended pattern for driving Lettuce from `suspend` code.
 *
 * The connection is opened lazily on first use (under a [Mutex]) rather than in the constructor, so
 * constructing a [RedisRateLimiter] — and `RateLimiter` with a Redis provider — never blocks
 * on, or requires, a reachable server. This matches platform-go, whose `redis.NewClient` builds a
 * lazily connecting client.
 *
 * TODO(cluster): platform-go's `NewRedisRateLimiter` builds a `redis.NewClusterClient` when more than
 * one address is configured (`Config.clusterMode()`). Cluster support here (a `RedisClusterClient`-backed
 * adapter) is a documented seam; a single-node [LettuceRedisClient] is the primary backend. The
 * sliding-window script is client-agnostic and already in place for when the cluster adapter lands.
 */
public class LettuceRedisClient(
    private val config: RedisRateLimitingConfig,
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

    override suspend fun evalInt(
        script: String,
        keys: List<String>,
        args: List<Any>,
    ): Long {
        // Redis coerces every ARGV to a bulk string on the wire, so render the mixed Long/String args
        // to strings here; ScriptOutputType.INTEGER decodes the reply to a Long.
        val values = args.map { it.toString() }.toTypedArray()
        return commands()
            .eval<Long>(script, ScriptOutputType.INTEGER, keys.toTypedArray(), *values)
            .await()
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
}
