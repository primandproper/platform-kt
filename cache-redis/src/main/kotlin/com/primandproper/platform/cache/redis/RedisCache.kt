package com.primandproper.platform.cache.redis

import com.primandproper.platform.cache.BatchCache
import com.primandproper.platform.cache.CacheCodec
import com.primandproper.platform.cache.redis.slots.slotForKey
import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.ensureCircuitBreaker
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
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
 * Every command runs under the injected [circuitBreaker], mirroring platform-go's `cache/redis`. Go
 * drives the raw `CanProceed/Succeeded/Failed` quartet by hand and, crucially, **degrades gracefully
 * when the breaker is open** — `Get` returns a miss, `GetMany` an empty map, and `Set`/`Delete`/
 * `SetMany` are no-ops (never `ErrCircuitBroken`), so a struggling Redis sheds load instead of
 * cascading failures into callers. The coroutine-native breaker here is `execute`-shaped (it throws
 * `ErrCircuitBroken` when open), so each transport call goes through [degrade]/[degradeUnit], which
 * catch that sentinel and return the same miss/no-op Go does. A transport error inside the call still
 * propagates and counts as a breaker failure; a healthy miss (Go's `redis.Nil`, surfaced as a `null`
 * from [RedisClient]) returns normally and counts as a success. [ping] stays outside the breaker,
 * matching Go's `Ping`, which never consults it. Encoding/decoding sits outside the breaker too, so a
 * codec failure is not counted against the transport (mirroring Go, which does not `Failed()` on
 * encode/decode errors).
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
    private val circuitBreaker: CircuitBreaker,
) : BatchCache<T> {
    /**
     * @param client the command surface; [LettuceRedisClient] in production, a fake in tests.
     * @param codec turns `T` into its stored string form and back.
     * @param expiration the TTL applied to writes.
     * @param cluster whether the backing client is a Redis Cluster (governs slot bucketing).
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
     */
    public constructor(
        client: RedisClient,
        codec: CacheCodec<T>,
        expiration: Duration,
        cluster: Boolean = false,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
        circuitBreaker: CircuitBreaker? = null,
    ) : this(Observer(NAME, logger, tracerProvider), client, codec, expiration, cluster, ensureCircuitBreaker(circuitBreaker))

    private val ttlMillis: Long get() = expiration.inWholeMilliseconds

    override suspend fun get(key: String): T? =
        o11y.span("Get") {
            set(Keys.NAME, key)
            // Transport under the breaker; an open breaker degrades to a miss (null), a missing key is
            // also a healthy miss (null, not a failure). Decoding stays outside the breaker.
            val raw = degrade<String?>(null) { client.get(key) } ?: return@span null
            codec.decode(raw)
        }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        o11y.span("Set") {
            set(Keys.NAME, key)
            val encoded = codec.encode(value)
            degradeUnit { client.set(key, encoded, ttlMillis) }
        }
    }

    override suspend fun delete(key: String) {
        o11y.span("Delete") {
            set(Keys.NAME, key)
            degradeUnit { client.del(key) }
        }
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> =
        o11y.span("GetMany") {
            set(Keys.LENGTH, keys.size)
            if (keys.isEmpty()) {
                return@span emptyMap()
            }
            // An open breaker degrades to an empty result, matching Go's GetMany.
            degrade(emptyMap<String, T>()) {
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
        }

    override suspend fun setMany(items: Map<String, T>) {
        o11y.span("SetMany") {
            set(Keys.LENGTH, items.size)
            if (items.isEmpty()) {
                return@span
            }
            // Encode every value first so a single bad value fails the batch before any write.
            val encoded = items.mapValues { (_, value) -> codec.encode(value) }
            degradeUnit {
                for (group in slotGroups(encoded.keys.toList())) {
                    client.setBatch(group, group.map { encoded.getValue(it) }, ttlMillis)
                }
            }
        }
    }

    override suspend fun ping() {
        o11y.span("Ping") {
            client.ping()
        }
    }

    /**
     * Runs [block] under the breaker but returns [onOpen] instead of throwing when the breaker is open
     * — the coroutine-native analog of Go's `if CannotProceed() { return <miss/empty> }`. A transport
     * failure inside [block] still propagates (and counts as a breaker failure); only `ErrCircuitBroken`
     * (the open-breaker rejection) is turned into graceful degradation.
     */
    private suspend fun <R> degrade(
        onOpen: R,
        block: suspend () -> R,
    ): R =
        try {
            circuitBreaker.execute(block)
        } catch (e: PlatformException) {
            if (isError(e, ErrCircuitBroken)) onOpen else throw e
        }

    /** [degrade] for a write op: an open breaker makes the call a silent no-op, as Go's Set/Delete/SetMany do. */
    private suspend fun degradeUnit(block: suspend () -> Unit): Unit = degrade(Unit, block)

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
