package com.primandproper.platform.distributedlock.postgres

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.ensureCircuitBreaker
import com.primandproper.platform.distributedlock.ErrEmptyKey
import com.primandproper.platform.distributedlock.ErrInvalidTTL
import com.primandproper.platform.distributedlock.ErrLockNotAcquired
import com.primandproper.platform.distributedlock.ErrLockNotHeld
import com.primandproper.platform.distributedlock.Lock
import com.primandproper.platform.distributedlock.Locker
import com.primandproper.platform.errors.isError
import com.primandproper.platform.errors.newError
import com.primandproper.platform.identifiers.newUlid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.Operation
import com.primandproper.platform.observability.TracerProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * A Postgres-backed [Locker] over session-scoped advisory locks. Port of platform-go's
 * `distributedlock/postgres.locker`.
 *
 * Each [acquire] reserves a dedicated [AdvisoryLockConnection] and runs `pg_try_advisory_lock`; the
 * matching `pg_advisory_unlock` runs on that same session, so the lock is genuinely
 * session-scoped. The connection is pinned for the lock's whole lifetime and tracked in an
 * outstanding set keyed by an opaque ownership token; [close] releases every one. On a failed unlock
 * the connection is force-discarded (see [AdvisoryLockConnection.discard]) so the advisory lock can't
 * leak back into the pool.
 *
 * TTL is advisory only: Postgres advisory locks have no server-side expiry, so the handle tracks it
 * client-side (via the injectable [now] clock) purely to honor the Lock contract — once expired, the
 * handle is treated as no longer held and its pinned connection is freed. [Lock.refresh] is a
 * `SELECT 1` liveness probe that lets the caller bump their local TTL bookkeeping; it does not extend
 * anything on the server.
 *
 * The [CircuitBreaker] wraps each backend interaction; platform-go's
 * `CannotProceed()`/`Succeeded()`/`Failed()` trio is folded into [CircuitBreaker.execute] — an open
 * breaker short-circuits with [ErrCircuitBroken], a real backend error counts a failure, and the
 * healthy control-flow outcomes (contention, pool saturation, lost ownership) return normally and
 * count a success, so they never trip the breaker.
 *
 * TODO(metrics): Go records acquire/release/refresh/contend/error counters and a latency histogram
 * through a metrics provider; there is no metrics pillar in platform-kt's observability-api yet.
 */
public class PostgresLocker internal constructor(
    private val o11y: Observer,
    private val client: AdvisoryLockClient,
    private val circuitBreaker: CircuitBreaker,
    private val namespace: Int,
    private val connWaitTimeout: Duration,
    private val now: () -> Long,
) : Locker {
    /**
     * @param config namespace + connection-wait settings.
     * @param client the database surface; a [JdbcAdvisoryLockClient] in production, a fake in tests.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param circuitBreaker optional breaker; defaults to an always-closed no-op breaker.
     */
    public constructor(
        config: PostgresLockConfig,
        client: AdvisoryLockClient,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
        circuitBreaker: CircuitBreaker? = null,
    ) : this(
        Observer(NAME, logger, tracerProvider),
        client,
        ensureCircuitBreaker(circuitBreaker),
        config.namespace,
        config.effectiveConnWaitTimeout(),
        System::currentTimeMillis,
    )

    private val mutex = Mutex()
    private val outstanding: MutableMap<String, PostgresLock> = HashMap()

    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock {
        val op = o11y.begin("Acquire")
        try {
            op.set(Keys.NAME, key).set("lock.ttl", ttl)

            if (key.isEmpty()) throw ErrEmptyKey
            if (!ttl.isPositive()) throw ErrInvalidTTL

            val lockId = hashLockID(namespace, key)
            op.set("lock.id", lockId)

            val outcome =
                try {
                    circuitBreaker.execute {
                        val conn =
                            try {
                                client.reserve(connWaitTimeout)
                            } catch (saturated: PoolSaturatedException) {
                                // A saturated pool is contention, not a failure.
                                return@execute AcquireOutcome.Contended
                            }
                        val granted =
                            try {
                                conn.tryAdvisoryLock(lockId)
                            } catch (t: Throwable) {
                                runCatching { conn.release() }
                                throw t
                            }
                        if (!granted) {
                            runCatching { conn.release() }
                            AcquireOutcome.Contended
                        } else {
                            AcquireOutcome.Acquired(conn)
                        }
                    }
                } catch (t: Throwable) {
                    if (isError(t, ErrCircuitBroken)) throw t
                    throw op.error(t, "reserving postgres advisory lock")
                }

            return when (outcome) {
                AcquireOutcome.Contended -> throw ErrLockNotAcquired
                is AcquireOutcome.Acquired -> {
                    val token = newUlid()
                    val handle =
                        PostgresLock(this, outcome.conn, key, token, lockId, ttl, now() + ttl.inWholeMilliseconds, now)
                    mutex.withLock { outstanding[token] = handle }
                    handle
                }
            }
        } finally {
            op.end()
        }
    }

    override suspend fun ping() {
        client.ping()
    }

    /**
     * Releases all outstanding locks held by this Locker, then closes the client. After [close],
     * individual handles see [ErrLockNotHeld] on release/refresh. Surfaces the first release failure.
     */
    override suspend fun close() {
        val handles =
            mutex.withLock {
                val snapshot = outstanding.values.toList()
                outstanding.clear()
                snapshot
            }
        var firstError: Throwable? = null
        for (handle in handles) {
            try {
                releaseLocked(null, handle)
            } catch (t: Throwable) {
                if (firstError == null) firstError = t
            }
        }
        client.close()
        firstError?.let { throw it }
    }

    // The unlock path called by lock handles.
    private suspend fun release(handle: PostgresLock) {
        val op = o11y.begin("Release")
        try {
            op.set(Keys.NAME, handle.key).set("lock.id", handle.lockId)

            val outcome =
                try {
                    circuitBreaker.execute {
                        val present = mutex.withLock { outstanding.remove(handle.token) != null }
                        if (!present) {
                            ReleaseOutcome.NotHeld
                        } else {
                            // The TTL may have elapsed: the caller no longer owns the lock, but the
                            // advisory lock / connection is still pinned — free it either way.
                            releaseLocked(op, handle)
                            if (handle.expired()) ReleaseOutcome.NotHeld else ReleaseOutcome.Released
                        }
                    }
                } catch (t: Throwable) {
                    if (isError(t, ErrCircuitBroken)) throw t
                    throw op.error(t, "releasing postgres advisory lock")
                }
            if (outcome == ReleaseOutcome.NotHeld) throw ErrLockNotHeld
        } finally {
            op.end()
        }
    }

    // The liveness-probe refresh path called by lock handles. Postgres advisory locks have no native
    // TTL, so this only verifies the session is still alive and lets the caller bump local TTL.
    private suspend fun refresh(
        handle: PostgresLock,
        ttl: Duration,
    ) {
        val op = o11y.begin("Refresh")
        try {
            op.set(Keys.NAME, handle.key).set("lock.id", handle.lockId).set("lock.ttl", ttl)
            if (!ttl.isPositive()) throw ErrInvalidTTL

            val outcome =
                try {
                    circuitBreaker.execute {
                        val present = mutex.withLock { outstanding.containsKey(handle.token) }
                        if (!present) {
                            RefreshOutcome.NotHeld
                        } else if (handle.expired()) {
                            // Expired: drop it and free the pinned lock/conn, then report not-held.
                            mutex.withLock { outstanding.remove(handle.token) }
                            runCatching { releaseLocked(op, handle) }
                                .onFailure { op.acknowledge(it, "releasing expired postgres advisory lock during refresh") }
                            RefreshOutcome.NotHeld
                        } else {
                            // SELECT 1 verifies the conn is alive without altering server state; a dead
                            // session is a real failure that should count against the breaker.
                            val alive = runCatching { handle.conn.isAlive() }.getOrDefault(false)
                            if (!alive) throw LIVENESS_LOST
                            RefreshOutcome.Refreshed
                        }
                    }
                } catch (t: Throwable) {
                    when {
                        t === LIVENESS_LOST -> throw ErrLockNotHeld
                        isError(t, ErrCircuitBroken) -> throw t
                        else -> throw op.error(t, "refreshing postgres advisory lock")
                    }
                }
            if (outcome == RefreshOutcome.NotHeld) throw ErrLockNotHeld
        } finally {
            op.end()
        }
    }

    // Runs the unlock SQL on the pinned conn and returns it to the pool. On any failure the conn is
    // force-discarded so a session that may still hold the advisory lock isn't reused. Does not touch
    // the outstanding map — the caller does that under the mutex before calling this.
    private suspend fun releaseLocked(
        op: Operation?,
        handle: PostgresLock,
    ) {
        var ok = false
        try {
            val unlocked = handle.conn.advisoryUnlock(handle.lockId)
            if (!unlocked) {
                // pg_advisory_unlock returns false when this session did not hold the lock. We believed
                // we held it, so this is a real inconsistency — surface it rather than a clean release.
                throw newError("pg_advisory_unlock reported the lock was not held by this session")
            }
            ok = true
        } finally {
            if (ok) {
                runCatching { handle.conn.release() }.onFailure { acknowledge(op, it, "returning postgres conn to pool") }
            } else {
                runCatching { handle.conn.discard() }
                    .onFailure { acknowledge(op, it, "discarding postgres conn after failed unlock") }
            }
        }
    }

    private fun acknowledge(
        op: Operation?,
        error: Throwable,
        message: String,
    ) {
        if (op != null) op.acknowledge(error, message) else o11y.logger.error(message, error)
    }

    private sealed interface AcquireOutcome {
        data object Contended : AcquireOutcome

        class Acquired(val conn: AdvisoryLockConnection) : AcquireOutcome
    }

    private enum class ReleaseOutcome { Released, NotHeld }

    private enum class RefreshOutcome { Refreshed, NotHeld }

    // The postgres-backed Lock handle. Each handle owns a dedicated connection. Nested so it can reach
    // the locker's private release/refresh.
    private class PostgresLock(
        private val locker: PostgresLocker,
        val conn: AdvisoryLockConnection,
        override val key: String,
        val token: String,
        val lockId: Long,
        ttl: Duration,
        expiresAt: Long,
        private val now: () -> Long,
    ) : Lock {
        private var currentTtl: Duration = ttl
        private var expiresAt: Long = expiresAt

        override val ttl: Duration get() = currentTtl

        // Whether the client-side TTL has elapsed. Postgres has no server-side expiry, so this tracks
        // it locally to honor the Lock contract, mirroring the redis/memory backends.
        fun expired(): Boolean = now() > expiresAt

        override suspend fun release() {
            locker.release(this)
        }

        override suspend fun refresh(ttl: Duration) {
            locker.refresh(this, ttl)
            currentTtl = ttl
            expiresAt = now() + ttl.inWholeMilliseconds
        }
    }

    private companion object {
        const val NAME = "postgres_distributed_lock"

        // Internal marker so a dead-session liveness probe counts as a breaker failure (matching Go's
        // errCounter+Failed) yet is translated to ErrLockNotHeld outside execute without recording a
        // spurious operation error via op.error.
        val LIVENESS_LOST: Throwable = newError("postgres session liveness lost")
    }
}

/**
 * Derives a stable [Long] lock id from a (namespace, key) pair using FNV-64a. The namespace prefix
 * lets independent services share a Postgres cluster without colliding on the advisory-lock id space.
 * Port of platform-go's `hashLockID`.
 */
internal fun hashLockID(
    namespace: Int,
    key: String,
): Long {
    var hash = 0xcbf29ce484222325uL.toLong() // FNV-64a offset basis
    val prime = 0x100000001b3L // FNV-64a prime
    val header =
        byteArrayOf(
            (namespace ushr 24).toByte(),
            (namespace ushr 16).toByte(),
            (namespace ushr 8).toByte(),
            namespace.toByte(),
        )
    for (b in header) {
        hash = hash xor (b.toLong() and 0xff)
        hash *= prime
    }
    for (b in key.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (b.toLong() and 0xff)
        hash *= prime
    }
    return hash
}
