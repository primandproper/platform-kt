package com.primandproper.platform.database

import java.sql.PreparedStatement
import java.time.Instant
import javax.sql.DataSource

/*
 * The relational-database querier and connection contract. Port of the interface set in platform-go's
 * `database/database.go` — `Scanner`, `ResultIterator`, `SQLQueryExecutor`, the transaction-manager
 * subsets, and `Client`.
 *
 * Two faithfulness notes carry across the whole file:
 *  - Go threads a `context.Context` through every I/O method; this port suspends instead, so the
 *    context propagates implicitly through the coroutine (the same move :cache-api makes).
 *  - Go's querier subsets are cut out of `*sql.DB`/`*sql.Tx`. The JVM analog of `*sql.DB` is JDBC's
 *    [javax.sql.DataSource]; the analog of `*sql.Stmt` is [java.sql.PreparedStatement]. Both are JDK
 *    types, so the abstraction stays pure-JVM and driver-free — the Exposed/Postgres backend that
 *    fulfils it lives in `:database-exposed`.
 */

/** Any database response that can populate destinations from a row. Port of Go's `Scanner` (`sql.Row[s]`). */
public interface Scanner {
    /**
     * Copies the columns of the current row into [dest], in order. Mirrors Go's `Scan(dest ...any)`;
     * the out-parameter style is preserved from the Go interface so repository code and the mock read
     * the same way.
     */
    public fun scan(vararg dest: Any?)
}

/** An iterable database response (`sql.Rows`). Port of Go's `ResultIterator`. */
public interface ResultIterator : Scanner, AutoCloseable {
    /** Advances to the next row, returning `false` when the result set is exhausted. */
    public fun next(): Boolean

    /** Returns the error that terminated iteration, or `null` if it ended cleanly. Mirrors `sql.Rows.Err`. */
    public fun err(): Throwable?
}

/**
 * The outcome of a write. Port of Go's `sql.Result`, which the `SQLQueryExecutor` subset returns from
 * `ExecContext`.
 */
public interface SqlResult {
    /** The auto-generated id of the last inserted row, where the driver supports it. */
    public fun lastInsertId(): Long

    /** The number of rows the statement affected. */
    public fun rowsAffected(): Long
}

/**
 * The read/write query surface shared by `sql.DB` and `sql.Tx`. Port of Go's `SQLQueryExecutor`, made
 * coroutine-native: each method suspends where Go took a `context.Context`.
 */
public interface SqlQueryExecutor {
    /** Executes a non-row-returning statement, returning its [SqlResult]. Mirrors `ExecContext`. */
    public suspend fun exec(
        query: String,
        vararg args: Any?,
    ): SqlResult

    /** Prepares a reusable statement. Mirrors `PrepareContext`. */
    public suspend fun prepare(query: String): PreparedStatement

    /** Runs a row-returning query, returning an iterator over the results. Mirrors `QueryContext`. */
    public suspend fun query(
        query: String,
        vararg args: Any?,
    ): ResultIterator

    /** Runs a query expected to return at most one row. Mirrors `QueryRowContext`. */
    public suspend fun queryRow(
        query: String,
        vararg args: Any?,
    ): Scanner
}

/** The rollback subset of `sql.Tx`. Port of Go's `SQLTransactionManager`. */
public interface SqlTransactionManager {
    /** Aborts the transaction. Mirrors `sql.Tx.Rollback`. */
    public suspend fun rollback()
}

/** A value that is both a querier and a rollback-capable transaction. Port of Go's combined subset interface. */
public interface SqlQueryExecutorAndTransactionManager : SqlQueryExecutor, SqlTransactionManager

/**
 * The primary database client. Port of platform-go's `database.Client`.
 *
 * Go exposes `WriteDB()`/`ReadDB()` returning `*sql.DB`; here they are [writeDataSource] and
 * [readDataSource] returning [javax.sql.DataSource]. A split read/write pair is preserved so a service
 * can point reads at a replica. [close] is [AutoCloseable.close] so the client works with
 * `use { }`.
 */
public interface DatabaseClient : AutoCloseable {
    /** The pool writes go to. */
    public val writeDataSource: DataSource

    /** The pool reads go to; may be the same object as [writeDataSource] when only one is configured. */
    public val readDataSource: DataSource

    /** The client's notion of "now", overridable for tests. Mirrors Go's `CurrentTime`. */
    public fun currentTime(): Instant

    /**
     * Rolls back [tx], logging (not throwing) on failure — the same fire-and-forget contract as Go's
     * `RollbackTransaction(ctx, tx)`, typically called from a deferred/`finally` cleanup.
     */
    public suspend fun rollbackTransaction(tx: SqlQueryExecutorAndTransactionManager)
}
