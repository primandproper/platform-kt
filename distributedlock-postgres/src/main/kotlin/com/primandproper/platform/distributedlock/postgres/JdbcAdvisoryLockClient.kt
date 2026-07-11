package com.primandproper.platform.distributedlock.postgres

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.sql.Connection
import java.sql.SQLTimeoutException
import java.sql.SQLTransientConnectionException
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * The production [AdvisoryLockClient], backed by a JDBC [DataSource]. Port of the platform-go
 * postgres locker's use of `database.Client` / `*sql.Conn`.
 *
 * Blocking JDBC calls run on [Dispatchers.IO] so they never stall the calling coroutine's thread. A
 * pool that refuses to hand out a connection is mapped to [PoolSaturatedException] so the locker
 * reports contention rather than an opaque failure — this covers both the JDBC-standard
 * [SQLTimeoutException] and Hikari's [SQLTransientConnectionException] (its sibling), which is what
 * HikariCP actually throws once its own `connectionTimeout` elapses on an exhausted pool.
 *
 * [reserve] also honors the caller's `waitBudget`: a positive budget bounds the wait with
 * [withTimeout] (the analog of Go's `connWaitTimeout`), so a saturated pool surfaces as
 * [PoolSaturatedException] even when the underlying pool's own timeout is longer or unset; a
 * non-positive budget disables the bound and waits as long as the pool allows. If the budget elapses
 * after the pool has already handed out a connection, that connection is closed rather than leaked.
 *
 * A pooled DataSource is assumed: [AdvisoryLockConnection.release] returns the connection to the pool,
 * and [AdvisoryLockConnection.discard] force-evicts it (via `Connection.abort`, falling back to
 * `close`) so a session that may still hold an advisory lock is not reused. Configure the pool with
 * enough connections that concurrently held locks do not starve unrelated queries.
 */
public class JdbcAdvisoryLockClient(
    private val dataSource: DataSource,
) : AdvisoryLockClient {
    override suspend fun reserve(waitBudget: Duration): AdvisoryLockConnection =
        try {
            // A positive budget bounds the wait; a non-positive one waits as long as the pool allows.
            if (waitBudget.isPositive()) withTimeout(waitBudget) { openConnection() } else openConnection()
        } catch (timeout: TimeoutCancellationException) {
            // The wait budget elapsed before the pool freed a connection: treat as saturation.
            throw PoolSaturatedException()
        }

    private suspend fun openConnection(): AdvisoryLockConnection =
        withContext(Dispatchers.IO) {
            val connection =
                try {
                    dataSource.connection
                } catch (saturated: SQLTransientConnectionException) {
                    // HikariCP signals pool exhaustion with this SQLTimeoutException sibling.
                    throw PoolSaturatedException()
                } catch (timeout: SQLTimeoutException) {
                    throw PoolSaturatedException()
                }
            // If the wait budget (or the caller) cancelled us while we blocked for the connection, hand
            // it straight back to the pool instead of leaking a checked-out connection into a discarded
            // result — a leaked connection is exactly what saturates the pool.
            if (!isActive) {
                runCatching { connection.close() }
                throw CancellationException("connection wait budget elapsed")
            }
            JdbcAdvisoryLockConnection(connection)
        }

    override suspend fun ping() {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { it.executeQuery().use { rs -> rs.next() } }
            }
        }
    }

    override suspend fun close() {
        // The DataSource owns pool lifecycle; nothing connection-scoped to release here.
    }
}

/** A single reserved JDBC connection, running the advisory-lock SQL on [Dispatchers.IO]. */
internal class JdbcAdvisoryLockConnection(
    private val connection: Connection,
) : AdvisoryLockConnection {
    override suspend fun tryAdvisoryLock(lockId: Long): Boolean = queryBoolean("SELECT pg_try_advisory_lock(?)", lockId)

    override suspend fun advisoryUnlock(lockId: Long): Boolean = queryBoolean("SELECT pg_advisory_unlock(?)", lockId)

    override suspend fun isAlive(): Boolean =
        withContext(Dispatchers.IO) {
            connection.prepareStatement("SELECT 1").use { it.executeQuery().use { rs -> rs.next() } }
        }

    override suspend fun release() {
        withContext(Dispatchers.IO) { connection.close() }
    }

    override suspend fun discard() {
        withContext(Dispatchers.IO) {
            // Abort ends the physical connection (and its Postgres session) instead of returning it to
            // the pool, releasing any advisory lock it still held. Fall back to close if unsupported.
            runCatching { connection.abort(Runnable::run) }.onFailure { connection.close() }
        }
    }

    private suspend fun queryBoolean(
        sql: String,
        lockId: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, lockId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }
}
