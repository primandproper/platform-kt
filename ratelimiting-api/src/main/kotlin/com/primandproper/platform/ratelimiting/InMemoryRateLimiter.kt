package com.primandproper.platform.ratelimiting

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

/**
 * A per-key, in-memory token-bucket [RateLimiter]. Port of platform-go's `inMemoryRateLimiter`.
 *
 * Each distinct key gets its own [TokenBucket] with the shared [requestsPerSec] / [burstSize] budget,
 * created on first use and held in a [ConcurrentHashMap]; `computeIfAbsent` gives the atomic
 * get-or-create Go gets from `sync.Map.LoadOrStore`. [close] drops every per-key bucket so the map
 * does not retain memory past shutdown, mirroring Go's `limiters.Clear()`.
 *
 * Every `allow` opens an [Observer] span recording the key and the allow/deny outcome — the
 * platform-kt analog of Go incrementing `allowedCounter` / `rejectedCounter`. Go records those through
 * a metrics provider; there is no metrics pillar in platform-kt's observability-api, so the counters
 * themselves are a documented `TODO(metrics)` seam (the same descope `:cache-api` makes).
 *
 * @param requestsPerSec the steady token refill rate; a non-positive rate never refills (the bucket
 *   allows only the initial [burstSize], matching `rate.Limit(0)`).
 * @param burstSize the bucket capacity — the most requests admitted in an instant.
 */
public class InMemoryRateLimiter internal constructor(
    private val o11y: Observer,
    private val requestsPerSec: Double,
    private val burstSize: Int,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : RateLimiter {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching platform-go's
     *   `NewInMemoryRateLimiter(nil, ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        requestsPerSec: Double,
        burstSize: Int,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer(NAME, logger, tracerProvider), requestsPerSec, burstSize, TimeSource.Monotonic)

    // Exposed to same-module tests so they can assert per-key bucket count, mirroring the Go tests
    // ranging over `inMemoryRateLimiter.limiters`.
    internal val limiters: ConcurrentHashMap<String, TokenBucket> = ConcurrentHashMap()

    override suspend fun allow(key: String): Boolean =
        o11y.span("Allow") {
            set(Keys.NAME, key)
            val allowed = getOrCreateLimiter(key).allow()
            // TODO(metrics): Go adds 1 to allowedCounter/rejectedCounter here; recorded on the span
            // for now (see the module note on the missing metrics pillar).
            set(ALLOWED, allowed)
            allowed
        }

    private fun getOrCreateLimiter(key: String): TokenBucket =
        limiters.computeIfAbsent(key) {
            TokenBucket(requestsPerSec, burstSize, timeSource)
        }

    override fun close() {
        limiters.clear()
    }

    private companion object {
        const val NAME = "in_memory_rate_limiter"
        const val ALLOWED = "allowed"
    }
}
