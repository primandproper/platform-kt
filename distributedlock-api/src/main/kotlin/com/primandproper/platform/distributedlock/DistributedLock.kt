package com.primandproper.platform.distributedlock

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.newError
import kotlin.time.Duration

/*
 * Port of platform-go's root `distributedlock` package: the Locker/Lock contract plus the exported
 * error sentinels. A distributed lock is a pessimistic mutual-exclusion atom for coordinating
 * exclusive access to a named resource across processes; provider implementations live in the
 * backend modules (`:distributedlock-redis`, `:distributedlock-postgres`) and the in-memory / noop
 * backends live here.
 *
 * The interface is intentionally narrow — Acquire/Release/Refresh, with no built-in retry loop or
 * queueing. Callers compose Acquire with `:retry`, the `:circuitbreaking` breaker, or their own
 * backoff. Higher-level concerns (leader election, distributed cron, exactly-once batch execution)
 * are compositions on top of this atom and live in consuming applications, not in platform.
 *
 * Provider semantics differ in one respect: the redis and memory providers enforce TTLs natively,
 * while the postgres provider's TTL is advisory only — the underlying `pg_advisory_lock` is held
 * until Release is called or the dedicated session is closed. See `:distributedlock-postgres`.
 */

/**
 * Signals that [Locker.acquire] could not obtain the lock immediately because another caller
 * currently holds it. There is no internal retry — callers wrap Acquire with retry/backoff
 * themselves. Mirrors platform-go's `ErrLockNotAcquired`.
 */
public val ErrLockNotAcquired: PlatformException = newError("lock not acquired")

/**
 * Signals that [Lock.release] or [Lock.refresh] was called on a lock the caller no longer owns —
 * TTL expiration, the lock being stolen by another caller after expiration, double-release, or (for
 * the postgres provider) the underlying connection being closed out from under us. Mirrors
 * platform-go's `ErrLockNotHeld`.
 */
public val ErrLockNotHeld: PlatformException = newError("lock not held")

/** Signals a nil provider config was passed to a constructor. Mirrors platform-go's `ErrNilConfig`. */
public val ErrNilConfig: PlatformException = newError("nil distributedlock config")

/** Signals a non-positive TTL was supplied to Acquire or Refresh. Mirrors platform-go's `ErrInvalidTTL`. */
public val ErrInvalidTTL: PlatformException = newError("invalid lock TTL")

/** Signals an empty key was supplied to Acquire. Mirrors platform-go's `ErrEmptyKey`. */
public val ErrEmptyKey: PlatformException = newError("empty lock key")

/**
 * Signals a nil database client was passed to a postgres-backed provider. Mirrors platform-go's
 * `ErrNilDatabaseClient`.
 */
public val ErrNilDatabaseClient: PlatformException = newError("nil database client")

/**
 * The manager atom. It hands out [Lock] handles keyed by string. Locker implementations must be safe
 * for concurrent use; the [Lock] handles they return are owned by the coroutine that called [acquire]
 * and are NOT safe to share. Port of platform-go's `distributedlock.Locker`.
 *
 * Every method suspends: Go threads a `context.Context` through each call, and the coroutine analog
 * is a `suspend` function whose caller's [kotlinx.coroutines.CoroutineScope] carries cancellation.
 */
public interface Locker {
    /**
     * Attempts to acquire the lock named [key] with the supplied [ttl]. Throws [ErrLockNotAcquired]
     * immediately if the lock is currently held by another caller (there is no internal retry),
     * [ErrEmptyKey] for an empty [key], or [ErrInvalidTTL] for a non-positive [ttl].
     */
    public suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock

    /** Verifies the underlying backend is reachable, throwing if it is not. */
    public suspend fun ping()

    /**
     * Releases any backend resources held by the Locker. Outstanding [Lock] handles obtained from
     * this Locker may become invalid after [close].
     */
    public suspend fun close()
}

/**
 * The handle returned from [Locker.acquire]. It carries the ownership token internally and is the
 * only way to release or refresh the lock. Handles are owned by a single coroutine — they must not
 * be shared. Port of platform-go's `distributedlock.Lock`.
 */
public interface Lock {
    /** The lock name this handle owns. */
    public val key: String

    /**
     * The configured expiration for this lock at the time it was last acquired or refreshed. It is
     * not adjusted as time passes.
     */
    public val ttl: Duration

    /**
     * Releases the lock. Throws [ErrLockNotHeld] if the caller no longer owns the lock (expiration,
     * theft after expiration, double-release).
     */
    public suspend fun release()

    /** Extends the lock's TTL to [ttl]. Throws [ErrLockNotHeld] if the caller no longer owns the lock. */
    public suspend fun refresh(ttl: Duration)
}
