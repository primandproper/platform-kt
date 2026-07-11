package com.primandproper.platform.messagequeue.redis

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
    logger: Logger = NoopLogger,
) : RedisPubSubClient {
    private val logger: Logger = logger
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
        // Attach the buffering listener (in the subscription's init) BEFORE issuing SUBSCRIBE, so no
        // payload delivered once the server-side subscription is active is lost in the window before the
        // cold messages() flow is collected (P2-3).
        val subscription = LettuceSubscription(c, conn, channel, logger)
        conn.async().subscribe(channel.encodeToByteArray()).await()
        return subscription
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

/**
 * A single Lettuce pub/sub [Subscription]. Its `RedisPubSubListener` is attached eagerly (at
 * construction, before the caller issues SUBSCRIBE) and feeds every payload into a bounded [Channel]
 * that [messages] drains as a cold [Flow]. Two design points, both raised in review:
 *
 * - **No lost-message window (P2-3):** because the listener is buffering from the moment the
 *   subscription exists — not only once the cold flow is collected — traffic arriving between
 *   SUBSCRIBE and the first `collect` is retained in the channel and delivered on collection.
 * - **The Netty event loop is never blocked (P2-4):** the listener uses non-blocking [Channel.trySend]
 *   (not `trySendBlocking`), so a slow or absent collector can never stall Lettuce's I/O thread. When
 *   the [BUFFER_CAPACITY]-slot buffer is full, or after the subscription is closed, the payload is
 *   dropped and the drop is logged rather than silently swallowed.
 */
internal class LettuceSubscription(
    private val client: LettuceClient,
    private val connection: StatefulRedisPubSubConnection<ByteArray, ByteArray>,
    channel: String,
    logger: Logger,
) : Subscription {
    private val logger: Logger = logger.withName("redis_pubsub_subscription").withValue("channel", channel)

    private val buffer: Channel<ByteArray> = Channel(capacity = BUFFER_CAPACITY)

    private val listener =
        object : RedisPubSubAdapter<ByteArray, ByteArray>() {
            override fun message(
                channel: ByteArray,
                message: ByteArray,
            ) {
                val result = buffer.trySend(message)
                if (result.isFailure) {
                    this@LettuceSubscription.logger.warn(
                        if (result.isClosed) {
                            "dropping redis pub/sub message: subscription buffer closed"
                        } else {
                            "dropping redis pub/sub message: subscription buffer full (capacity=$BUFFER_CAPACITY)"
                        },
                    )
                }
            }
        }

    init {
        connection.addListener(listener)
    }

    override fun messages(): Flow<ByteArray> = buffer.receiveAsFlow()

    override suspend fun close() {
        connection.removeListener(listener)
        buffer.close()
        connection.close()
        client.shutdown()
    }
}

/**
 * Bounded buffer between Lettuce's Netty I/O thread and the message collector. Sized to absorb a burst
 * without blocking the event loop; a sustained overflow (a collector slower than the publish rate)
 * drops the excess and logs it. Was the `callbackFlow` default (64) before P2-4.
 */
private const val BUFFER_CAPACITY = 1024
