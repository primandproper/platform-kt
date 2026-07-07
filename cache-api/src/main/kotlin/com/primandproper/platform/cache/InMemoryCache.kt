package com.primandproper.platform.cache

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory [BatchCache] backed by a [ConcurrentHashMap]. Port of platform-go's
 * `cache/memory.inMemoryCacheImpl`.
 *
 * Every method opens an [Observer] span, recording the key (or batch length) on both pillars —
 * mirroring `i.o11y.Begin(ctx)` / `op.Set("name", key)`. Go additionally records hit/miss/set/delete
 * counters and a latency histogram through a metrics provider; there is no metrics pillar in
 * platform-kt's observability-api, so those are a documented `TODO(metrics)` seam here (the same
 * descope `:circuitbreaking` makes).
 *
 * The Go impl guards a plain `map` with an `sync.RWMutex`; a [ConcurrentHashMap] gives the same
 * thread-safe single-key semantics without a lock, and `getMany`/`setMany` iterate it — a batch is
 * not required to be atomic against concurrent single-key writes, matching Go, where the batch takes
 * the lock but callers do not rely on cross-key atomicity.
 */
public class InMemoryCache<T : Any> internal constructor(
    private val o11y: Observer,
) : BatchCache<T> {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching platform-go's
     *   `NewInMemoryCache(nil, ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(Observer(NAME, logger, tracerProvider))

    // Exposed to same-module tests so they can assert map size, mirroring the Go tests reaching into
    // `inMemoryCacheImpl.cache`.
    internal val cache: ConcurrentHashMap<String, T> = ConcurrentHashMap()

    override suspend fun get(key: String): T? =
        o11y.span("Get") {
            set(Keys.NAME, key)
            cache[key]
        }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        o11y.span("Set") {
            set(Keys.NAME, key)
            cache[key] = value
        }
    }

    override suspend fun delete(key: String) {
        o11y.span("Delete") {
            set(Keys.NAME, key)
            cache.remove(key)
        }
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> =
        o11y.span("GetMany") {
            set(Keys.LENGTH, keys.size)
            buildMap {
                for (key in keys) {
                    val value = cache[key] ?: continue
                    put(key, value)
                }
            }
        }

    override suspend fun setMany(items: Map<String, T>) {
        o11y.span("SetMany") {
            set(Keys.LENGTH, items.size)
            cache.putAll(items)
        }
    }

    override suspend fun ping() {
        o11y.span("Ping") {
            logger.debug("ping")
        }
    }

    private companion object {
        const val NAME = "in_memory_cache"
    }
}
