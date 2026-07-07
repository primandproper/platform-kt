package com.primandproper.platform.cache.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.primandproper.platform.cache.BatchCache
import com.primandproper.platform.cache.CacheCodec
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

/**
 * A persistent, disk-backed [BatchCache] for Android, backed by Jetpack DataStore (Preferences).
 * Implements the same [BatchCache] contract as the in-memory and Redis backends, so an Android caller
 * swaps a `DataStoreCache` in wherever a `Cache` is expected and survives process death.
 *
 * TTL semantics: each write stores a [TtlEnvelope] carrying an absolute expiry (`now + ttl`); reads
 * drop — and lazily evict — any entry past its expiry, so no background sweeper is needed. A
 * non-positive [ttl] stores entries without expiry. This is the disk analog of the Redis backend's
 * PX-based expiry.
 *
 * Values cross the persistence boundary through an injected [CacheCodec] — serialization stays at the
 * edge, exactly as in the Go source and the Redis backend here.
 *
 * Each operation opens an [Observer] span recording the key/length on both pillars, matching the
 * instrumentation convention of the other backends.
 *
 * @param dataStore the Preferences DataStore to persist into (typically one per cache namespace).
 * @param codec turns `T` into its stored string form and back.
 * @param ttl the time-to-live applied to writes; non-positive means entries never expire.
 * @param clock supplies the current epoch-millis; injectable so expiry is deterministic in tests.
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop tracing.
 */
public class DataStoreCache<T : Any>(
    private val dataStore: DataStore<Preferences>,
    private val codec: CacheCodec<T>,
    private val ttl: Duration,
    private val clock: () -> Long = System::currentTimeMillis,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
) : BatchCache<T> {
    private val o11y: Observer = Observer(NAME, logger, tracerProvider)

    override suspend fun get(key: String): T? =
        o11y.span("Get") {
            set(Keys.NAME, key)
            val prefs = dataStore.data.first()
            readValid(prefs, key) ?: run {
                // Lazily evict an expired/absent entry so the store doesn't accumulate dead keys.
                if (prefs.contains(stringPreferencesKey(key))) evict(key)
                null
            }
        }

    override suspend fun set(
        key: String,
        value: T,
    ) {
        o11y.span("Set") {
            set(Keys.NAME, key)
            val stored = TtlEnvelope.encode(expiryFor(), codec.encode(value))
            dataStore.edit { it[stringPreferencesKey(key)] = stored }
        }
    }

    override suspend fun delete(key: String) {
        o11y.span("Delete") {
            set(Keys.NAME, key)
            evict(key)
        }
    }

    override suspend fun getMany(keys: List<String>): Map<String, T> =
        o11y.span("GetMany") {
            set(Keys.LENGTH, keys.size)
            if (keys.isEmpty()) {
                return@span emptyMap()
            }
            val prefs = dataStore.data.first()
            buildMap {
                for (key in keys) {
                    val value = readValid(prefs, key) ?: continue
                    put(key, value)
                }
            }
        }

    override suspend fun setMany(items: Map<String, T>) {
        o11y.span("SetMany") {
            set(Keys.LENGTH, items.size)
            if (items.isEmpty()) {
                return@span
            }
            val expiresAt = expiryFor()
            dataStore.edit { prefs ->
                for ((key, value) in items) {
                    prefs[stringPreferencesKey(key)] = TtlEnvelope.encode(expiresAt, codec.encode(value))
                }
            }
        }
    }

    /** Always succeeds: the on-device store is always reachable. */
    override suspend fun ping() {
        o11y.span("Ping") {
            logger.debug("ping")
        }
    }

    private fun readValid(
        prefs: Preferences,
        key: String,
    ): T? {
        val raw = prefs[stringPreferencesKey(key)] ?: return null
        val (expiresAt, payload) = TtlEnvelope.decode(raw) ?: return null
        if (TtlEnvelope.isExpired(expiresAt, clock())) return null
        return codec.decode(payload)
    }

    private suspend fun evict(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private fun expiryFor(): Long = if (ttl.isPositive()) clock() + ttl.inWholeMilliseconds else 0L

    private companion object {
        const val NAME = "datastore_cache"
    }
}
