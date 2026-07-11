package com.primandproper.platform.database.postgres

import com.primandproper.platform.database.MapRow
import com.primandproper.platform.database.PreparedHandle
import com.primandproper.platform.database.Row
import com.primandproper.platform.database.SqlQueryExecutor
import com.primandproper.platform.database.SqlResult
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.Operation
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant
import javax.sql.DataSource

/**
 * A JDBC-backed [SqlQueryExecutor] over a [javax.sql.DataSource] — the Exposed/Postgres module's
 * implementation of the redesigned query surface (see P3-3). Runs the platform-go query verbs against
 * arbitrary SQL text, materializing each result row into a detached [MapRow] so the returned [Flow]s
 * are safe to collect independently of the JDBC connection lifecycle.
 *
 * All blocking JDBC work hops to [Dispatchers.IO]; every verb opens an [Observer] span (`Exec`,
 * `Query`, `QueryOne`, `WithPrepared`) recording `db.system`, and — only when [logQueries] — the raw
 * `db.statement`, never leaking SQL by default. Each call borrows a connection from the pool and
 * returns it in a `use {}`; [withPrepared] holds one connection + statement open for the block's
 * duration and closes both when the block returns.
 *
 * This is a per-pool executor: build one over [DatabaseClient.readDataSource] for reads and one over
 * [DatabaseClient.writeDataSource] for writes (or use [ExposedDatabaseClient.readExecutor]/
 * [ExposedDatabaseClient.writeExecutor]).
 *
 * TODO(tx): a transaction-scoped [com.primandproper.platform.database.SqlQueryExecutorAndTransactionManager]
 * (Go's `BeginTx`) is a separate seam — this executor is auto-commit per statement, matching the fact
 * that the api `DatabaseClient` exposes only fire-and-forget `rollbackTransaction`.
 */
public class JdbcSqlQueryExecutor internal constructor(
    private val dataSource: DataSource,
    private val o11y: Observer,
    private val logQueries: Boolean,
) : SqlQueryExecutor {
    /**
     * @param dataSource the pool this executor borrows connections from.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param logQueries whether the raw SQL text may be recorded on spans; defaults to `false`.
     */
    public constructor(
        dataSource: DataSource,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
        logQueries: Boolean = false,
    ) : this(dataSource, Observer(NAME, logger, tracerProvider), logQueries)

    override suspend fun exec(
        query: String,
        vararg args: Any?,
    ): SqlResult =
        o11y.span("Exec") {
            recordQuery(query)
            withContext(Dispatchers.IO) {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { ps ->
                        bindArgs(ps, args)
                        val affected = ps.executeUpdate().toLong()
                        SqlResult(lastInsertId = readGeneratedKey(ps), rowsAffected = affected)
                    }
                }
            }
        }

    override fun query(
        query: String,
        vararg args: Any?,
    ): Flow<Row> =
        flow {
            val rows =
                o11y.span("Query") {
                    recordQuery(query)
                    withContext(Dispatchers.IO) {
                        dataSource.connection.use { conn ->
                            conn.prepareStatement(query).use { ps ->
                                bindArgs(ps, args)
                                ps.executeQuery().use { rs -> materializeAll(rs) }
                            }
                        }
                    }
                }
            for (row in rows) emit(row)
        }

    override suspend fun queryOne(
        query: String,
        vararg args: Any?,
    ): Row? =
        o11y.span("QueryOne") {
            recordQuery(query)
            withContext(Dispatchers.IO) {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(query).use { ps ->
                        bindArgs(ps, args)
                        ps.executeQuery().use { rs -> if (rs.next()) materializeRow(rs) else null }
                    }
                }
            }
        }

    override suspend fun <T> withPrepared(
        sql: String,
        block: suspend (PreparedHandle) -> T,
    ): T =
        o11y.span("WithPrepared") {
            recordQuery(sql)
            val conn = withContext(Dispatchers.IO) { dataSource.connection }
            try {
                val ps = withContext(Dispatchers.IO) { conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) }
                try {
                    block(JdbcPreparedHandle(ps))
                } finally {
                    withContext(Dispatchers.IO) { ps.close() }
                }
            } finally {
                withContext(Dispatchers.IO) { conn.close() }
            }
        }

    private fun Operation.recordQuery(sql: String) {
        set("db.system", "postgresql")
        if (logQueries) spanOnly("db.statement", sql)
    }

    internal companion object {
        const val NAME: String = "db_query"
    }
}

/** A [PreparedHandle] over a live [PreparedStatement], only reachable inside [JdbcSqlQueryExecutor.withPrepared]. */
private class JdbcPreparedHandle(
    private val ps: PreparedStatement,
) : PreparedHandle {
    override fun bind(
        index: Int,
        value: Any?,
    ): PreparedHandle {
        ps.setObject(index, toJdbc(value))
        return this
    }

    override fun bindAll(vararg values: Any?): PreparedHandle {
        ps.clearParameters()
        values.forEachIndexed { i, v -> ps.setObject(i + 1, toJdbc(v)) }
        return this
    }

    override suspend fun executeUpdate(): SqlResult =
        withContext(Dispatchers.IO) {
            val affected = ps.executeUpdate().toLong()
            SqlResult(lastInsertId = readGeneratedKey(ps), rowsAffected = affected)
        }

    override fun executeQuery(): Flow<Row> =
        flow {
            val rows = withContext(Dispatchers.IO) { ps.executeQuery().use { materializeAll(it) } }
            for (row in rows) emit(row)
        }
}

/** Binds positional [args] (1-based) onto [ps], converting kotlin.time/JDK types the driver does not accept natively. */
private fun bindArgs(
    ps: PreparedStatement,
    args: Array<out Any?>,
) {
    args.forEachIndexed { i, v -> ps.setObject(i + 1, toJdbc(v)) }
}

/** Converts a bind value into a JDBC-friendly form: an [Instant] becomes a `java.sql.Timestamp`; everything else passes through. */
private fun toJdbc(value: Any?): Any? =
    when (value) {
        is Instant -> java.sql.Timestamp.from(value)
        else -> value
    }

/** Reads the first generated key as a Long, tolerating drivers/statements that produced none. */
private fun readGeneratedKey(ps: PreparedStatement): Long =
    try {
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    } catch (_: java.sql.SQLException) {
        0L
    }

/** Reads the current row of [rs] into a detached [MapRow], keyed by column label. */
private fun materializeRow(rs: ResultSet): Row {
    val meta = rs.metaData
    val values = LinkedHashMap<String, Any?>(meta.columnCount)
    for (col in 1..meta.columnCount) {
        values[meta.getColumnLabel(col)] = rs.getObject(col)
    }
    return MapRow(values)
}

/** Drains [rs] into a list of detached [MapRow]s. */
private fun materializeAll(rs: ResultSet): List<Row> {
    val rows = ArrayList<Row>()
    while (rs.next()) rows += materializeRow(rs)
    return rows
}
