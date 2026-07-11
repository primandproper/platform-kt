package com.primandproper.platform.distributedlock

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.observability.SuspendCloseable
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
 *
 * Distributed-lock error types: in platform-go these are `var Err… = errors.New(…)` sentinels matched
 * via errors.Is; here they are exception CLASSES thrown fresh at each site and matched by type. The
 * nil-config / nil-database-client sentinels are dropped — Kotlin's non-null types already forbid what
 * they modelled (the provider constructors take non-null configs/clients).
 */

/**
 * Thrown when [Locker.acquire] could not obtain the lock immediately because another caller currently
 * holds it. There is no internal retry — callers wrap Acquire with retry/backoff themselves. Mirrors
 * platform-go's `ErrLockNotAcquired`.
 */
public class LockNotAcquiredException : PlatformException("lock not acquired")

/**
 * Thrown when [Lock.release] or [Lock.refresh] was called on a lock the caller no longer owns —
 * TTL expiration, the lock being stolen by another caller after expiration, double-release, or (for
 * the postgres provider) the underlying connection being closed out from under us. Mirrors
 * platform-go's `ErrLockNotHeld`.
 */
public class LockNotHeldException : PlatformException("lock not held")

/** Thrown when a non-positive TTL was supplied to Acquire or Refresh. Mirrors platform-go's `ErrInvalidTTL`. */
public class InvalidTtlException : PlatformException("invalid lock TTL")

/** Thrown when an empty key was supplied to Acquire. Mirrors platform-go's `ErrEmptyKey`. */
public class EmptyKeyException : PlatformException("empty lock key")

/**
 * The manager atom. It hands out [Lock] handles keyed by string. Locker implementations must be safe
 * for concurrent use; the [Lock] handles they return are owned by the coroutine that called [acquire]
 * and are NOT safe to share. Port of platform-go's `distributedlock.Locker`.
 *
 * Every method suspends: Go threads a `context.Context` through each call, and the coroutine analog
 * is a `suspend` function whose caller's [kotlinx.coroutines.CoroutineScope] carries cancellation.
 *
 * TTL-enforcement guarantees differ by backend and are NOT uniform behind this interface:
 * - the redis and memory backends enforce the TTL natively/server-side, so a crashed holder's lock is
 *   automatically released once the TTL elapses;
 * - the postgres backend's TTL is **advisory only**. `pg_advisory_lock` has no server-side expiry, so
 *   the lock (and the pooled connection pinning it) is held until [Lock.release] or [close] runs, or
 *   the session is torn down — a handle abandoned by a crashed owner is NOT reaped after its TTL.
 *
 * Callers that depend on TTL-based auto-release of a dead holder's lock must choose a backend that
 * enforces it. See `PostgresLocker` in `:distributedlock-postgres` for the full divergence note.
 */
public interface Locker : SuspendCloseable {
    /**
     * Attempts to acquire the lock named [key] with the supplied [ttl]. Throws [LockNotAcquiredException]
     * immediately if the lock is currently held by another caller (there is no internal retry),
     * [EmptyKeyException] for an empty [key], or [InvalidTtlException] for a non-positive [ttl].
     *
     * The [ttl]'s enforcement is backend-dependent — server-side for redis/memory, advisory-only for
     * postgres (see the type-level note above).
     */
    public suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock

    /** Verifies the underlying backend is reachable, throwing if it is not. */
    public suspend fun ping()

    /**
     * Releases any backend resources held by the Locker. Outstanding [Lock] handles obtained from
     * this Locker may become invalid after [close]. Shares the platform-wide [SuspendCloseable] closer
     * type (P3-12), so a Locker can be bracketed with `use { }`.
     */
    override suspend fun close()
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
     * Releases the lock. Throws [LockNotHeldException] if the caller no longer owns the lock (expiration,
     * theft after expiration, double-release).
     */
    public suspend fun release()

    /** Extends the lock's TTL to [ttl]. Throws [LockNotHeldException] if the caller no longer owns the lock. */
    public suspend fun refresh(ttl: Duration)
}
