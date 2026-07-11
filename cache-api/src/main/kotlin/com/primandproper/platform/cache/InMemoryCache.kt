package com.primandproper.platform.cache

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
 *
 * Writes carry the configured [expiry] as a per-entry deadline, and expiry is applied **lazily on
 * read** (an expired entry reads as a miss and is dropped) plus opportunistically on each write — the
 * same no-background-sweeper approach as `MemoryLocker`. Without this, entries would live forever
 * (stale reads) and the heap would grow unbounded. A hard [maxEntries] cap bounds the heap even for a
 * non-expiring config or a write stream faster than entries expire, evicting the soonest-to-expire
 * entries first. Elapsed time is measured off an injected [kotlin.time.TimeSource] ([timeSource],
 * [TimeSource.Monotonic] in production) so expiry is deterministic in tests without real sleeps.
 * Expiry only needs *elapsed* time (a per-entry TTL), never wall-clock, so a monotonic source is the
 * right base — this is the same modeling `:circuitbreaking` uses for its reset deadline. (The Android
 * `DataStoreCache`, by contrast, persists absolute expiry across process death and must stay on the
 * wall clock.)
 */
public class InMemoryCache<T : Any> internal constructor(
    private val o11y: Observer,
    private val expiry: Duration = Duration.INFINITE,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : BatchCache<T> {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching platform-go's
     *   `NewInMemoryCache(nil, ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param expiry the TTL applied to every write; defaults to [Duration.INFINITE] (never expires),
     *   so a caller that does not wire an expiry keeps the historical never-expire behavior. The
     *   `provideCache` factory passes [CacheConfig.expiry] through.
     * @param maxEntries the hard cap on retained entries, a heap guard for a non-expiring config.
     */
    public constructor(
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
        expiry: Duration = Duration.INFINITE,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ) : this(Observer(NAME, logger, tracerProvider), expiry, maxEntries, TimeSource.Monotonic)

    // A stored value together with the [TimeMark] taken when it was written. Expiry is derived by
    // comparing that mark's elapsed time against the cache-wide [expiry] TTL (all entries share one
    // TTL, so write order is expiry order). Internal rather than private only because the internal
    // test-visible [cache] field's value type must be at least as visible as the field.
    internal class Entry<V>(
        val value: V,
        val writtenAt: TimeMark,
    )

    // Exposed to same-module tests so they can assert map size, mirroring the Go tests reaching into
    // `inMemoryCacheImpl.cache`.
    internal val cache: ConcurrentHashMap<String, Entry<T>> = ConcurrentHashMap()

    override suspend fun get(key: String): T? =
        o11y.span("Get") {
            set(Keys.NAME, key)
            live(key)
        }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        o11y.span("Set") {
            set(Keys.NAME, key)
            cache[key] = Entry(value, timeSource.markNow())
            evictExpiredAndOverflow()
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
                    val value = live(key) ?: continue
                    put(key, value)
                }
            }
        }

    override suspend fun setMany(items: Map<String, T>) {
        o11y.span("SetMany") {
            set(Keys.LENGTH, items.size)
            val writtenAt = timeSource.markNow()
            for ((key, value) in items) {
                cache[key] = Entry(value, writtenAt)
            }
            evictExpiredAndOverflow()
        }
    }

    override suspend fun ping() {
        o11y.span("Ping") {
            logger.debug("ping")
        }
    }

    // Returns the live value at [key], dropping it as a miss if its TTL has elapsed (lazy expiry). The
    // conditional remove only reclaims the exact expired entry, never a concurrently-written newer one.
    private fun live(key: String): T? {
        val entry = cache[key] ?: return null
        if (entry.isExpired()) {
            cache.remove(key, entry)
            return null
        }
        return entry.value
    }

    // Whether [this] entry's TTL has elapsed. A non-positive/infinite [expiry] never expires; otherwise
    // the entry is dead once at least [expiry] has elapsed since it was written.
    private fun Entry<T>.isExpired(): Boolean = expiry.isPositive() && !expiry.isInfinite() && writtenAt.elapsedNow() >= expiry

    // Opportunistic sweep: drop every expired entry, then, if still over the cap, evict the entries
    // closest to expiring until back at the cap. With a single cache-wide TTL, the oldest writes (the
    // largest elapsed time) are the soonest to expire. Bounds the heap without a background thread.
    private fun evictExpiredAndOverflow() {
        cache.entries.removeAll { it.value.isExpired() }
        val overflow = cache.size - maxEntries
        if (overflow > 0) {
            cache.entries
                .sortedByDescending { it.value.writtenAt.elapsedNow() }
                .take(overflow)
                .forEach { cache.remove(it.key, it.value) }
        }
    }

    private companion object {
        const val NAME = "in_memory_cache"

        // A generous default cap so ordinary use never evicts, while still bounding a pathological or
        // non-expiring workload.
        const val DEFAULT_MAX_ENTRIES = 10_000
    }
}
