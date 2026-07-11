package com.primandproper.platform.distributedlock.postgres

/**
 * The minimal Postgres surface [PostgresLocker] needs, split so the locker's connection-pinning logic
 * is unit-testable without a live database. Port of the parts of platform-go's `database.Client` /
 * `*sql.Conn` that the postgres locker actually uses; the split mirrors how `:cache-redis` factors a
 * `RedisLockClient` interface out for the same reason (advisory locks are Postgres-specific, so H2
 * cannot stand in — a fake does).
 *
 * [reserve] is the analog of Go's `db.WriteDB().Conn(ctx)`: it hands out a dedicated session that the
 * matching `pg_advisory_unlock` must run on. [ping] verifies reachability (Go pings the read DB).
 */
public interface AdvisoryLockClient {
    /**
     * Reserves a dedicated [AdvisoryLockConnection] from the write pool. [waitBudget] is the
     * best-effort bound on how long to wait (the analog of Go's `connWaitTimeout`); an adapter that
     * cannot honor it should ignore it. Throws [PoolSaturatedException] when the wait budget elapses
     * with every connection pinned by a held lock, so the locker can surface that as contention
     * rather than an opaque hang.
     */
    public suspend fun reserve(waitBudget: kotlin.time.Duration): AdvisoryLockConnection

    /** Verifies the database is reachable. */
    public suspend fun ping()

    /** Releases pooled resources. */
    public suspend fun close()
}

/**
 * A dedicated Postgres session, on which a single advisory lock is taken and later released. Every
 * method runs on the same underlying connection so `pg_advisory_unlock` targets the session that took
 * the lock. Port of the `*sql.Conn` operations the Go locker performs.
 */
public interface AdvisoryLockConnection {
    /** Runs `SELECT pg_try_advisory_lock($1)`, returning whether the lock was granted (no blocking). */
    public suspend fun tryAdvisoryLock(lockId: Long): Boolean

    /**
     * Runs `SELECT pg_advisory_unlock($1)`, returning whether this session actually held the lock.
     * A `false` result is an inconsistency the locker surfaces (the session should have held it).
     */
    public suspend fun advisoryUnlock(lockId: Long): Boolean

    /** Runs `SELECT 1` as a liveness probe, returning whether the session is still usable. */
    public suspend fun isAlive(): Boolean

    /** Returns the connection to the pool healthy (the analog of `*sql.Conn.Close()`). */
    public suspend fun release()

    /**
     * Force-discards the physical connection so its Postgres session ends and every advisory lock it
     * held is released. Used after a failed unlock, where returning the connection healthy would leak
     * the lock until the connection ages out — the analog of Go's `conn.Raw(... driver.ErrBadConn)`.
     */
    public suspend fun discard()
}

/**
 * Thrown by [AdvisoryLockClient.reserve] when the write pool is saturated by held locks and the wait
 * budget elapsed. The locker translates it into
 * [com.primandproper.platform.distributedlock.LockNotAcquiredException] — contention, not an error.
 */
public class PoolSaturatedException : RuntimeException("write connection pool saturated by held locks")
