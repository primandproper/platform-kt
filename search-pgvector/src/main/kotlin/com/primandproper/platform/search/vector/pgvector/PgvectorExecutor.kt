package com.primandproper.platform.search.vector.pgvector

/**
 * The minimal SQL command surface [PgvectorIndexManager] needs. It plays the role platform-go's
 * `database.Client` (`WriteDB()`/`ReadDB()` → `*sql.DB`) plays for the pgvector backend: an injectable
 * seam so the SQL-building and row-mapping logic can be unit-tested against a fake, without a live
 * Postgres. [JdbcPgvectorExecutor] is the production adapter over a `javax.sql.DataSource`.
 *
 * Statements use JDBC `?` placeholders (pgjdbc's parameter style), not Postgres' native `$N` — the one
 * deliberate divergence from the Go SQL, which targets `database/sql`/pgx.
 *
 * All methods suspend; the JDBC adapter dispatches each blocking call onto `Dispatchers.IO`.
 */
public interface PgvectorExecutor {
    /**
     * Runs [block] inside a single transaction, committing if it returns normally and rolling back if
     * it throws. Used for the schema migration and the multi-row upsert, so a mid-batch failure rolls
     * back the rows already written, matching Go's `BeginTx`/`Commit`/`Rollback` usage.
     */
    public suspend fun transaction(block: suspend (PgvectorTransaction) -> Unit)

    /** Executes a single write statement outside any transaction (delete, wipe). */
    public suspend fun execute(
        sql: String,
        params: List<Any?>,
    )

    /** Runs a read query and returns the materialized rows (query). */
    public suspend fun query(
        sql: String,
        params: List<Any?>,
    ): List<PgvectorRow>
}

/** A statement executor scoped to an open transaction, handed to [PgvectorExecutor.transaction]'s block. */
public interface PgvectorTransaction {
    /** Executes one write statement on the transaction's connection. */
    public suspend fun execute(
        sql: String,
        params: List<Any?>,
    )
}

/** One materialized result row, read by column name. Values are captured before the result set closes. */
public interface PgvectorRow {
    /** The text value of [column]. */
    public fun string(column: String): String

    /** The text value of [column], or `null` when the column is SQL NULL (e.g. a `jsonb` payload). */
    public fun stringOrNull(column: String): String?

    /** The floating-point value of [column] (the computed distance). */
    public fun double(column: String): Double
}
