package com.primandproper.platform.distributedlock.postgres

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Default bound on how long Acquire waits to reserve a connection. See [PostgresLockConfig.connWaitTimeout]. */
public val DEFAULT_CONN_WAIT_TIMEOUT: Duration = 5.seconds

/**
 * Configures a Postgres-backed distributed locker. Port of platform-go's
 * `distributedlock/postgres.Config`.
 *
 * @param namespace mixed into the lock-id hash so independent services that share a Postgres cluster
 *   do not collide on the same advisory-lock id space. Any [Int] is acceptable; defaults to `0`.
 * @param connWaitTimeout bounds how long Acquire waits to reserve a connection from the write pool.
 *   Each held lock pins one connection for its whole lifetime, so a saturated pool would otherwise
 *   make Acquire block indefinitely. When the wait is exceeded, Acquire fails with
 *   [com.primandproper.platform.distributedlock.ErrLockNotAcquired] instead of blocking. [Duration.ZERO]
 *   uses [DEFAULT_CONN_WAIT_TIMEOUT]; a negative value disables the bound (wait forever). Mirrors Go's
 *   `envDefault:"5s"`.
 */
public data class PostgresLockConfig(
    val namespace: Int = 0,
    val connWaitTimeout: Duration = DEFAULT_CONN_WAIT_TIMEOUT,
) {
    /** The wait bound actually applied: [DEFAULT_CONN_WAIT_TIMEOUT] when [connWaitTimeout] is zero. */
    public fun effectiveConnWaitTimeout(): Duration = if (connWaitTimeout == Duration.ZERO) DEFAULT_CONN_WAIT_TIMEOUT else connWaitTimeout

    /**
     * Validates the config. Namespace has no upper bound; any [Int] is acceptable — so this is a
     * total no-op today, kept for parity with Go's `ValidateWithContext` and as a seam for future
     * constraints.
     */
    public fun validate() {
        // No constraints: any namespace / wait timeout is acceptable.
    }
}
