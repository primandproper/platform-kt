package com.primandproper.platform.messagequeue.redis

import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.lettuce.core.RedisClient as LettuceClient
import java.time.Duration as JavaDuration

/**
 * The production [RedisPubSubClient], adapting Lettuce. Publishing uses a normal connection; each
 * subscription opens its own pub/sub connection whose `RedisPubSubListener` pushes payloads into a
 * `callbackFlow`. Every async command's `RedisFuture` (a `CompletionStage`) is bridged to a coroutine
 * with `kotlinx.coroutines.future.await`.
 *
 * The publish connection is opened lazily on first use (under a [Mutex]) rather than in the
 * constructor, so constructing a provider — and the selection factory with a Redis provider — never
 * blocks on, or requires, a reachable server, matching platform-go's lazily-connecting
 * `redis.NewClient`. Raw bytes are carried end-to-end via Lettuce's [ByteArrayCodec]; channel names
 * are UTF-8.
 *
 * TODO(cluster): platform-go's `buildRedisClient` also constructs a `redis.NewClusterClient` for
 * multi-address / `Cluster` configs. A `RedisClusterClient`-backed adapter is a documented seam
 * ([RedisMessageQueueConfig.clusterMode]); a single-node client is the primary backend here.
 */
public class LettuceRedisPubSubClient(
    private val config: RedisMessageQueueConfig,
) : RedisPubSubClient {
    private val mutex = Mutex()

    @Volatile private var client: LettuceClient? = null

    @Volatile private var connection: StatefulRedisConnection<ByteArray, ByteArray>? = null

    @Volatile private var commandsRef: RedisAsyncCommands<ByteArray, ByteArray>? = null

    private suspend fun commands(): RedisAsyncCommands<ByteArray, ByteArray> {
        commandsRef?.let { return it }
        return mutex.withLock {
            commandsRef?.let { return it }
            val c = LettuceClient.create(redisUri())
            val conn = c.connect(ByteArrayCodec.INSTANCE)
            client = c
            connection = conn
            conn.async().also { commandsRef = it }
        }
    }

    private fun redisUri(): RedisURI {
        val (host, port) = parseAddress(config.queueAddresses.first())
        return RedisURI.Builder.redis(host, port)
            .apply {
                config.username?.let { withAuthentication(it, (config.password ?: "").toCharArray()) }
                    ?: config.password?.let { withPassword(it.toCharArray()) }
            }
            .withTimeout(JavaDuration.ofSeconds(1))
            .build()
    }

    override suspend fun publish(
        channel: String,
        message: ByteArray,
    ): Long = commands().publish(channel.encodeToByteArray(), message).await()

    override suspend fun ping() {
        commands().ping().await()
    }

    override suspend fun subscribe(channel: String): Subscription {
        // A dedicated client + pub/sub connection per subscription, so closing one subscription never
        // disturbs another (and never touches the shared publish connection).
        val c = LettuceClient.create(redisUri())
        val conn = c.connectPubSub(ByteArrayCodec.INSTANCE)
        conn.async().subscribe(channel.encodeToByteArray()).await()
        return LettuceSubscription(c, conn)
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

/** A single Lettuce pub/sub [Subscription], bridging the listener's callbacks to a cold [callbackFlow]. */
internal class LettuceSubscription(
    private val client: LettuceClient,
    private val connection: StatefulRedisPubSubConnection<ByteArray, ByteArray>,
) : Subscription {
    override fun messages(): Flow<ByteArray> =
        callbackFlow {
            val listener =
                object : RedisPubSubAdapter<ByteArray, ByteArray>() {
                    override fun message(
                        channel: ByteArray,
                        message: ByteArray,
                    ) {
                        trySendBlocking(message)
                    }
                }
            connection.addListener(listener)
            awaitClose { connection.removeListener(listener) }
        }

    override suspend fun close() {
        connection.close()
        client.shutdown()
    }
}
