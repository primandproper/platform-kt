package com.primandproper.platform.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.sql.DataSource

/*
 * The relational-database querier and connection contract. Originally a transliteration of platform-go's
 * `database/database.go` (`Scanner`, `ResultIterator`, `SQLQueryExecutor`, the transaction-manager
 * subsets, and `Client`), redesigned to be Kotlin-idiomatic (see P3-3):
 *
 *  - Go's `Scanner.Scan(dest ...any)` out-parameter style is gone. Rows expose typed accessors
 *    ([Row]) and callers project them through a `(Row) -> T` mapper — a value that is safe to test and
 *    that the mock can trivially simulate.
 *  - Go's `ResultIterator` (advance-then-poll-`Err()`) is gone. Row-returning queries return a cold
 *    [Flow] of [Row] (or of the mapped `T`); iteration errors surface as the flow throwing on
 *    collection, the coroutine-native equivalent of `Rows.Err()`.
 *  - `prepare(): java.sql.PreparedStatement` is gone. Reusable statements are reached through the
 *    bracketed [SqlQueryExecutor.withPrepared], which hands the block a platform-owned [PreparedHandle]
 *    and closes the statement when the block returns — no blocking JDBC type leaks through the suspend
 *    abstraction.
 *
 * Faithfulness notes that still carry:
 *  - Go threads a `context.Context` through every I/O method; this port suspends instead, so the
 *    context propagates implicitly through the coroutine (the same move :cache-api makes).
 *  - The JVM analog of Go's `*sql.DB` is JDBC's [javax.sql.DataSource]; the [DatabaseClient] exposes a
 *    read/write pair of them so the abstraction stays pure-JVM and driver-free — the Exposed/Postgres
 *    backend that fulfils it lives in `:database-exposed`.
 */

/**
 * A single materialized database row exposing typed, by-name column accessors. Replaces Go's
 * `Scanner`/`sql.Row` out-parameter `Scan`.
 *
 * Each accessor comes in a non-null form (throws when the column is absent or SQL `NULL`) and a
 * nullable `*OrNull` form (returns `null` for an absent-or-`NULL` column) — the idiomatic replacement
 * for platform-go's `sql.NullString`/`sql.NullInt32` family (see [NullValues]). Column names are
 * matched case-insensitively, since Postgres folds unquoted identifiers to lower case.
 *
 * A [Row] is a detached snapshot: it is safe to keep and read after the query's [Flow] has moved on or
 * the underlying result set has closed. Build one with [MapRow].
 */
public interface Row {
    /** The set of column names present in this row (lower-cased). */
    public val columns: Set<String>

    /** Reports whether [column] is present and non-`NULL`. */
    public fun has(column: String): Boolean

    public fun string(column: String): String

    public fun stringOrNull(column: String): String?

    public fun long(column: String): Long

    public fun longOrNull(column: String): Long?

    public fun int(column: String): Int

    public fun intOrNull(column: String): Int?

    public fun boolean(column: String): Boolean

    public fun booleanOrNull(column: String): Boolean?

    public fun double(column: String): Double

    public fun doubleOrNull(column: String): Double?

    public fun float(column: String): Float

    public fun floatOrNull(column: String): Float?

    public fun instant(column: String): Instant

    public fun instantOrNull(column: String): Instant?

    public fun bytes(column: String): ByteArray

    public fun bytesOrNull(column: String): ByteArray?

    /** The raw, uncoerced value for [column] (or `null`), for callers that need an escape hatch. */
    public fun anyOrNull(column: String): Any?
}

/**
 * The default [Row], backed by an immutable snapshot of column name → value. Both the mock and the
 * Exposed backend build rows through this, and tests can construct one directly:
 * `MapRow(mapOf("id" to 1L, "name" to "ada"))`.
 *
 * Keys are stored lower-cased and looked up case-insensitively. Value coercion is lenient across the
 * numeric and temporal shapes a JDBC driver returns (e.g. a `java.sql.Timestamp` reads through
 * [instant], any [Number] widens through [long]/[int]/[double]/[float]).
 */
public class MapRow(
    values: Map<String, Any?>,
) : Row {
    private val backing: Map<String, Any?> = values.entries.associate { it.key.lowercase() to it.value }

    override val columns: Set<String> get() = backing.keys

    override fun has(column: String): Boolean = backing[column.lowercase()] != null

    override fun anyOrNull(column: String): Any? = backing[column.lowercase()]

    private fun raw(column: String): Any? {
        val key = column.lowercase()
        require(backing.containsKey(key)) { "no such column: \"$column\" (present: ${backing.keys})" }
        return backing[key]
    }

    private inline fun <T> nonNull(
        column: String,
        convert: (Any) -> T,
    ): T {
        val value = raw(column) ?: error("column \"$column\" is NULL; use the *OrNull accessor")
        return convert(value)
    }

    private inline fun <T> nullable(
        column: String,
        convert: (Any) -> T,
    ): T? = anyOrNull(column)?.let(convert)

    override fun string(column: String): String = nonNull(column) { it.asString() }

    override fun stringOrNull(column: String): String? = nullable(column) { it.asString() }

    override fun long(column: String): Long = nonNull(column) { it.asNumber().toLong() }

    override fun longOrNull(column: String): Long? = nullable(column) { it.asNumber().toLong() }

    override fun int(column: String): Int = nonNull(column) { it.asNumber().toInt() }

    override fun intOrNull(column: String): Int? = nullable(column) { it.asNumber().toInt() }

    override fun boolean(column: String): Boolean = nonNull(column) { it.asBoolean() }

    override fun booleanOrNull(column: String): Boolean? = nullable(column) { it.asBoolean() }

    override fun double(column: String): Double = nonNull(column) { it.asNumber().toDouble() }

    override fun doubleOrNull(column: String): Double? = nullable(column) { it.asNumber().toDouble() }

    override fun float(column: String): Float = nonNull(column) { it.asNumber().toFloat() }

    override fun floatOrNull(column: String): Float? = nullable(column) { it.asNumber().toFloat() }

    override fun instant(column: String): Instant = nonNull(column) { it.asInstant() }

    override fun instantOrNull(column: String): Instant? = nullable(column) { it.asInstant() }

    override fun bytes(column: String): ByteArray = nonNull(column) { it.asBytes() }

    override fun bytesOrNull(column: String): ByteArray? = nullable(column) { it.asBytes() }

    private fun Any.asString(): String = this as? String ?: toString()

    private fun Any.asNumber(): Number =
        when (this) {
            is Number -> this
            is Boolean -> if (this) 1 else 0
            is String -> toDoubleOrNull() ?: error("value \"$this\" is not numeric")
            else -> error("value of type ${this::class} is not numeric")
        }

    private fun Any.asBoolean(): Boolean =
        when (this) {
            is Boolean -> this
            is Number -> toInt() != 0
            is String -> equals("true", ignoreCase = true) || this == "t" || this == "1"
            else -> error("value of type ${this::class} is not boolean")
        }

    private fun Any.asBytes(): ByteArray =
        when (this) {
            is ByteArray -> this
            is String -> toByteArray()
            else -> error("value of type ${this::class} is not a byte array")
        }

    private fun Any.asInstant(): Instant =
        when (this) {
            is Instant -> this
            is java.sql.Timestamp -> toInstant()
            // java.sql.Date/Time.toInstant() throw by contract, so widen through epoch millis instead.
            is java.sql.Date -> Instant.ofEpochMilli(time)
            is java.sql.Time -> Instant.ofEpochMilli(time)
            is java.util.Date -> toInstant()
            is java.time.OffsetDateTime -> toInstant()
            is java.time.LocalDateTime -> toInstant(java.time.ZoneOffset.UTC)
            is Number -> Instant.ofEpochMilli(toLong())
            else -> error("value of type ${this::class} is not a timestamp")
        }
}

/**
 * The outcome of a write. Redesigned from Go's `sql.Result` getter interface to an immutable value.
 *
 * @property lastInsertId the auto-generated id of the last inserted row, where the driver supports it (0 otherwise).
 * @property rowsAffected the number of rows the statement affected.
 */
public data class SqlResult(
    val lastInsertId: Long = 0,
    val rowsAffected: Long = 0,
)

/**
 * A reusable prepared statement, owned by the platform (not a raw [java.sql.PreparedStatement]). Only
 * ever reached inside [SqlQueryExecutor.withPrepared], which closes the statement when the block
 * returns — so nothing here escapes the bracket.
 *
 * Parameters are 1-based, matching JDBC. [bind]/[bindAll] return `this` so binds can chain.
 */
public interface PreparedHandle {
    /** Binds [value] to 1-based parameter [index]. */
    public fun bind(
        index: Int,
        value: Any?,
    ): PreparedHandle

    /** Binds [values] to parameters 1..n in order, replacing any previously bound parameters. */
    public fun bindAll(vararg values: Any?): PreparedHandle

    /** Executes the currently-bound statement as a write, returning its [SqlResult]. */
    public suspend fun executeUpdate(): SqlResult

    /**
     * Executes the currently-bound statement as a query. The returned [Flow] is a detached snapshot of
     * the result set — safe to collect within the enclosing [SqlQueryExecutor.withPrepared] block.
     */
    public fun executeQuery(): Flow<Row>
}

/** Projects each [Row] of [PreparedHandle.executeQuery] through [mapper]. */
public fun <T> PreparedHandle.executeQuery(mapper: (Row) -> T): Flow<T> = executeQuery().map(mapper)

/**
 * The read/write query surface shared by a connection pool and a transaction. Redesigned from Go's
 * `SQLQueryExecutor`: row-returning calls hand back a cold [Flow] of [Row] instead of an iterator, and
 * reusable statements go through the bracketed [withPrepared] instead of leaking a
 * [java.sql.PreparedStatement].
 */
public interface SqlQueryExecutor {
    /** Executes a non-row-returning statement, returning its [SqlResult]. Mirrors Go's `ExecContext`. */
    public suspend fun exec(
        query: String,
        vararg args: Any?,
    ): SqlResult

    /**
     * Runs a row-returning query, returning a cold [Flow] of [Row]. The query executes when the flow is
     * collected; an error mid-iteration surfaces as the flow throwing (the coroutine-native `Rows.Err`).
     * Mirrors Go's `QueryContext`.
     */
    public fun query(
        query: String,
        vararg args: Any?,
    ): Flow<Row>

    /**
     * Runs a query expected to return at most one row, returning its [Row] or `null` when the result set
     * is empty. Mirrors Go's `QueryRowContext` (whose sole-row `Scan` reported no rows via
     * `ErrNoRows`).
     */
    public suspend fun queryOne(
        query: String,
        vararg args: Any?,
    ): Row?

    /**
     * Prepares [sql], hands [block] a [PreparedHandle] to bind and execute (repeatedly, for reuse), and
     * closes the statement when [block] returns — the bracketed replacement for Go's `PrepareContext` +
     * explicit `Stmt.Close`. Mirrors `Mutex.withLock`/`use {}` in shape.
     */
    public suspend fun <T> withPrepared(
        sql: String,
        block: suspend (PreparedHandle) -> T,
    ): T
}

/** Runs [query], projecting each [Row] through [mapper] into a `Flow<T>`. */
public fun <T> SqlQueryExecutor.query(
    query: String,
    vararg args: Any?,
    mapper: (Row) -> T,
): Flow<T> = query(query, *args).map(mapper)

/** Runs a single-row [query], projecting the [Row] through [mapper], or `null` when there is no row. */
public suspend fun <T> SqlQueryExecutor.queryOne(
    query: String,
    vararg args: Any?,
    mapper: (Row) -> T,
): T? = queryOne(query, *args)?.let(mapper)

/** The rollback subset of a transaction. Port of Go's `SQLTransactionManager`. */
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
