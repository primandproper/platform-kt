package com.primandproper.platform.cache.redis

import com.primandproper.platform.cache.BatchCache
import com.primandproper.platform.cache.CacheCodec
import com.primandproper.platform.cache.redis.slots.slotForKey
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlin.time.Duration

/**
 * A Redis-backed [BatchCache]. Port of platform-go's `cache/redis.redisCacheImpl`.
 *
 * Values are turned into their stored string form by an injected [CacheCodec] (Go gob-encodes into a
 * string; serialization stays at the boundary here). Each method opens an [Observer] span recording
 * the key or batch length on both pillars, mirroring `i.o11y.Begin(ctx)` / `op.Set("name", key)`; the
 * `span` scope records and rethrows any thrown command error exactly once.
 *
 * `getMany`/`setMany` respect Redis Cluster's same-slot requirement: in cluster mode keys are bucketed
 * by hash slot (via [slotForKey]) and each bucket is a separate round trip, while a single-node client
 * batches them all at once — matching Go's `slotGroups`.
 *
 * TODO(circuitbreaking): platform-go wraps every command with a `circuitbreaking.CircuitBreaker`
 * (short-circuiting reads to a miss and writes to a no-op while the breaker is open, and counting
 * successes/failures). That module is not part of this port's dependency set, so the breaker is a
 * documented seam — inject one here once `:circuitbreaking` is available downstream.
 *
 * TODO(metrics): Go records hit/miss/set/delete/error counters and a latency histogram through a
 * metrics provider; there is no metrics pillar in platform-kt's observability-api yet.
 */
public class RedisCache<T : Any> internal constructor(
    private val o11y: Observer,
    private val client: RedisClient,
    private val codec: CacheCodec<T>,
    private val expiration: Duration,
    private val isCluster: Boolean,
) : BatchCache<T> {
    /**
     * @param client the command surface; [LettuceRedisClient] in production, a fake in tests.
     * @param codec turns `T` into its stored string form and back.
     * @param expiration the TTL applied to writes.
     * @param cluster whether the backing client is a Redis Cluster (governs slot bucketing).
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        client: RedisClient,
        codec: CacheCodec<T>,
        expiration: Duration,
        cluster: Boolean = false,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(Observer(NAME, logger, tracerProvider), client, codec, expiration, cluster)

    private val ttlMillis: Long get() = expiration.inWholeMilliseconds

    override suspend fun get(key: String): T? =
        o11y.span("Get") {
            set(Keys.NAME, key)
            // A missing key is a healthy miss, not a failure: null propagates straight out.
            val raw = client.get(key) ?: return@span null
            codec.decode(raw)
        }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        o11y.span("Set") {
            set(Keys.NAME, key)
            client.set(key, codec.encode(value), ttlMillis)
        }
    }

    override suspend fun delete(key: String) {
        o11y.span("Delete") {
            set(Keys.NAME, key)
            client.del(key)
        }
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> =
        o11y.span("GetMany") {
            set(Keys.LENGTH, keys.size)
            if (keys.isEmpty()) {
                return@span emptyMap()
            }
            buildMap {
                for (group in slotGroups(keys)) {
                    val values = client.mget(group)
                    group.forEachIndexed { idx, key ->
                        val raw = values.getOrNull(idx) ?: return@forEachIndexed
                        put(key, codec.decode(raw))
                    }
                }
            }
        }

    override suspend fun setMany(items: Map<String, T>) {
        o11y.span("SetMany") {
            set(Keys.LENGTH, items.size)
            if (items.isEmpty()) {
                return@span
            }
            // Encode every value first so a single bad value fails the batch before any write.
            val encoded = items.mapValues { (_, value) -> codec.encode(value) }
            for (group in slotGroups(encoded.keys.toList())) {
                client.setBatch(group, group.map { encoded.getValue(it) }, ttlMillis)
            }
        }
    }

    override suspend fun ping() {
        o11y.span("Ping") {
            client.ping()
        }
    }

    /**
     * Splits [keys] into batches safe for a single MGET/EVAL: one group for a single-node client, one
     * group per hash slot for a cluster client. Mirrors Go's `slotGroups`/`groupBySlot`.
     */
    private fun slotGroups(keys: List<String>): List<List<String>> {
        if (!isCluster) {
            return listOf(keys)
        }
        return keys.groupBy { slotForKey(it) }.values.toList()
    }

    private companion object {
        const val NAME = "redis_cache"
    }
}
