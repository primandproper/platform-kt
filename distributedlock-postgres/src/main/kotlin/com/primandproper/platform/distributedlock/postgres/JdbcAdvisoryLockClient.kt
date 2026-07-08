package com.primandproper.platform.distributedlock.postgres

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLTimeoutException
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * The production [AdvisoryLockClient], backed by a JDBC [DataSource]. Port of the platform-go
 * postgres locker's use of `database.Client` / `*sql.Conn`.
 *
 * Blocking JDBC calls run on [Dispatchers.IO] so they never stall the calling coroutine's thread. A
 * pool that refuses to hand out a connection within its own connection-timeout throws
 * [SQLTimeoutException]; that is mapped to [PoolSaturatedException] so the locker reports contention
 * rather than an opaque failure.
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
        withContext(Dispatchers.IO) {
            try {
                JdbcAdvisoryLockConnection(dataSource.connection)
            } catch (timeout: SQLTimeoutException) {
                throw PoolSaturatedException()
            }
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
