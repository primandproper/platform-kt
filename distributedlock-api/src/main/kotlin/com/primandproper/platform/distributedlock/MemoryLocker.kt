package com.primandproper.platform.distributedlock

import com.primandproper.platform.identifiers.newUlid
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A single-process [Locker] backed by an in-memory map. Port of platform-go's `memory.locker`.
 *
 * It guards the held-lock map with a coroutine [Mutex] and applies lazy expiration on each [acquire]
 * — there is no background sweeper. Intended for tests, single-replica deployments, and as a clear
 * reference implementation of the lock semantics.
 *
 * Only [acquire] opens an [Observer] span (recording the key and TTL on both pillars), mirroring
 * `memory.locker`, whose `release`/`refresh` internals do not call `o11y.Begin`. Go additionally
 * records acquire/release/refresh/contend counters and a latency histogram through a metrics
 * provider; there is no metrics pillar in platform-kt's observability-api, so those are a documented
 * `TODO(metrics)` seam here (the same descope `:cache-api` and `:circuitbreaking` make).
 *
 * Elapsed time is measured off an injected [kotlin.time.TimeSource] ([timeSource],
 * [TimeSource.Monotonic] in production) so lease-expiry behavior is deterministic in tests without real
 * sleeps. Lease expiry only needs *elapsed* time (a per-lock TTL), never wall-clock, so a monotonic
 * source is the right base — the same modeling `:circuitbreaking` uses for its reset deadline.
 */
public class MemoryLocker internal constructor(
    private val o11y: Observer,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : Locker {
    /**
     * @param logger optional root logger; defaults to a noop logger, matching platform-go's
     *   `NewLocker(nil, ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer(NAME, logger, tracerProvider), TimeSource.Monotonic)

    // The current owner of a key: the ownership token and the [TimeMark] deadline its lease expires at.
    internal class Held(
        var expires: TimeMark,
        val token: String,
    )

    private val mutex = Mutex()

    // Exposed to same-module tests so they can assert map size / sweeping, mirroring the Go tests
    // reaching into `locker.held`.
    internal val held: MutableMap<String, Held> = HashMap()

    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock {
        val op = o11y.begin("Acquire")
        return try {
            op.set("lock.key", key).set("lock.ttl", ttl)

            if (key.isEmpty()) throw EmptyKeyException()
            if (!ttl.isPositive()) throw InvalidTtlException()

            mutex.withLock {
                // Opportunistically sweep entries whose TTL has elapsed. Per-key expiry below only
                // reclaims a key that is acquired again; without this sweep, keys acquired once and
                // never re-acquired would accumulate for the life of the process.
                held.entries.removeAll { it.value.expires.hasPassedNow() }

                val existing = held[key]
                if (existing != null && existing.expires.hasNotPassedNow()) {
                    throw LockNotAcquiredException()
                }

                val token = newUlid()
                held[key] = Held(expires = timeSource.markNow() + ttl, token = token)
                MemoryLock(this, key, token, ttl)
            }
        } finally {
            op.end()
        }
    }

    override suspend fun ping() {
        // Nothing to reach — an in-memory locker is always reachable.
    }

    /**
     * Drops all currently held locks. After [close], outstanding handles see [LockNotHeldException] on
     * release/refresh.
     */
    override suspend fun close() {
        mutex.withLock { held.clear() }
    }

    // The internal release path called by lock handles. Runs under the mutex and verifies the token
    // still owns the (unexpired) key.
    private suspend fun release(
        key: String,
        token: String,
    ) {
        mutex.withLock {
            val current = held[key]
            if (current == null || current.token != token || current.expires.hasPassedNow()) {
                throw LockNotHeldException()
            }
            held.remove(key)
        }
    }

    // The internal refresh path called by lock handles. Verifies the token still owns the (unexpired)
    // key before extending its TTL.
    private suspend fun refresh(
        key: String,
        token: String,
        ttl: Duration,
    ) {
        if (!ttl.isPositive()) throw InvalidTtlException()
        mutex.withLock {
            val current = held[key]
            if (current == null || current.token != token || current.expires.hasPassedNow()) {
                throw LockNotHeldException()
            }
            current.expires = timeSource.markNow() + ttl
        }
    }

    // The in-memory Lock handle. Nested so it can reach the locker's private release/refresh.
    private class MemoryLock(
        private val locker: MemoryLocker,
        override val key: String,
        private val token: String,
        ttl: Duration,
    ) : Lock {
        private var currentTtl: Duration = ttl
        override val ttl: Duration get() = currentTtl

        override suspend fun release() {
            locker.release(key, token)
        }

        override suspend fun refresh(ttl: Duration) {
            locker.refresh(key, token, ttl)
            currentTtl = ttl
        }
    }

    private companion object {
        const val NAME = "in_memory_distributed_lock"
    }
}
