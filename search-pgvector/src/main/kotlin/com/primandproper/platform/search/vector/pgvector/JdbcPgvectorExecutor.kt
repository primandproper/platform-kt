package com.primandproper.platform.search.vector.pgvector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * The production [PgvectorExecutor], adapting a `javax.sql.DataSource` (a pooled Postgres connection
 * source built over the `org.postgresql` driver). Each call borrows a connection, runs the statement,
 * and returns it to the pool.
 *
 * JDBC calls block, so every method is dispatched onto [Dispatchers.IO] and awaited from the `suspend`
 * method — the same bridge [com.primandproper.platform.search.text.elasticsearch.LowLevelElasticsearchClient]
 * uses. Nothing here is pgvector-specific: this adapter runs whatever SQL the manager builds, which is
 * why it can be proven end-to-end against in-memory H2 for the transaction/exec/query wiring even
 * though pgvector's `vector`-typed SQL only runs on real Postgres.
 */
public class JdbcPgvectorExecutor(
    private val dataSource: DataSource,
) : PgvectorExecutor {
    override suspend fun transaction(block: suspend (PgvectorTransaction) -> Unit) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val previousAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    block(JdbcTransaction(conn))
                    conn.commit()
                } catch (t: Throwable) {
                    conn.rollback()
                    throw t
                } finally {
                    conn.autoCommit = previousAutoCommit
                }
            }
        }
    }

    override suspend fun execute(
        sql: String,
        params: List<Any?>,
    ) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    bind(stmt, params)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun query(
        sql: String,
        params: List<Any?>,
    ): List<PgvectorRow> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    bind(stmt, params)
                    stmt.executeQuery().use { rs -> materialize(rs) }
                }
            }
        }

    private class JdbcTransaction(
        private val conn: Connection,
    ) : PgvectorTransaction {
        override suspend fun execute(
            sql: String,
            params: List<Any?>,
        ) {
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt, params)
                stmt.executeUpdate()
            }
        }
    }

    private companion object {
        fun bind(
            stmt: PreparedStatement,
            params: List<Any?>,
        ) {
            params.forEachIndexed { index, param ->
                val position = index + 1
                when (param) {
                    is ByteArray -> stmt.setBytes(position, param)
                    else -> stmt.setObject(position, param)
                }
            }
        }

        // Read every row into a plain holder up front so callers see stable values after the
        // ResultSet closes — the analog of scanning each row in Go's `for rows.Next()` loop.
        fun materialize(rs: ResultSet): List<PgvectorRow> {
            val meta = rs.metaData
            val labels = (1..meta.columnCount).map { meta.getColumnLabel(it).lowercase() }
            val out = mutableListOf<PgvectorRow>()
            while (rs.next()) {
                val values = HashMap<String, Any?>(labels.size)
                labels.forEachIndexed { i, label -> values[label] = rs.getObject(i + 1) }
                out += MaterializedRow(values)
            }
            return out
        }
    }

    private class MaterializedRow(
        private val values: Map<String, Any?>,
    ) : PgvectorRow {
        override fun string(column: String): String = stringOrNull(column) ?: error("column \"$column\" is null")

        override fun stringOrNull(column: String): String? = values[column.lowercase()]?.toString()

        override fun double(column: String): Double =
            when (val v = values[column.lowercase()]) {
                is Number -> v.toDouble()
                null -> error("column \"$column\" is null")
                else -> v.toString().toDouble()
            }
    }
}
