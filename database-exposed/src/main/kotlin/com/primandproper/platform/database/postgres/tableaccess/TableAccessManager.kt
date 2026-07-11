package com.primandproper.platform.database.postgres.tableaccess

import com.primandproper.platform.database.Manager
import com.primandproper.platform.errors.newError
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * The Postgres privileges the [TableAccessManager] understands. Port of platform-go's
 * `tableaccess.Privilege` constant set.
 */
public enum class Privilege(public val sql: String) {
    SELECT("SELECT"),
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    TRUNCATE("TRUNCATE"),
    REFERENCES("REFERENCES"),
    TRIGGER("TRIGGER"),
    CONNECT("CONNECT"), // database-level
    ;

    public companion object {
        /** Reports whether [name] names a known privilege. Port of Go's `isValidPrivilege`. */
        public fun isValid(name: String): Boolean = entries.any { it.sql == name }
    }
}

/**
 * Postgres administrative [Manager]: creating/dropping users and databases and granting table
 * privileges over a JDBC [javax.sql.DataSource]. Port of platform-go's `tableaccess.manager`.
 *
 * Identifiers and string literals are quoted with [quoteIdent]/[quoteLiteral] rather than
 * interpolated, because roles/databases/privileges cannot be passed as bind parameters in DDL — the
 * same reason the Go implementation quotes them by hand. Existence checks, which run against catalog
 * tables, do use bind parameters. Every method suspends and runs its JDBC work on [Dispatchers.IO].
 *
 * Every administrative operation is wrapped in an [Observer] span recording the role/database/table it
 * touches — mirroring the module's other instrumented classes (e.g. `ExposedDatabaseClient`) — so a
 * failed grant or user creation is traced and logged rather than vanishing. The password supplied to
 * [createUser] is deliberately never recorded on the span or the log.
 *
 * @param dataSource the JDBC pool DDL runs against.
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop tracing.
 */
public class TableAccessManager internal constructor(
    private val dataSource: DataSource,
    private val o11y: Observer,
) : Manager {
    public constructor(
        dataSource: DataSource,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(dataSource, Observer(NAME, logger, tracerProvider))

    override suspend fun createUser(
        username: String,
        password: String,
    ): Unit =
        o11y.span("CreateUser") {
            set("db.system", "postgresql")
            set("db.user.name", username)
            // The password is never recorded on the span or the log.
            execute(createUserSql(username, password))
        }

    override suspend fun deleteUser(username: String): Unit =
        o11y.span("DeleteUser") {
            set("db.system", "postgresql")
            set("db.user.name", username)
            execute(deleteUserSql(username))
        }

    override suspend fun createDatabase(
        dbName: String,
        owner: String,
    ): Unit =
        o11y.span("CreateDatabase") {
            set("db.system", "postgresql")
            set("db.name", dbName)
            set("db.owner", owner)
            execute(createDatabaseSql(dbName, owner))
        }

    override suspend fun deleteDatabase(dbName: String): Unit =
        o11y.span("DeleteDatabase") {
            set("db.system", "postgresql")
            set("db.name", dbName)
            execute(deleteDatabaseSql(dbName))
        }

    override suspend fun userExists(username: String): Boolean =
        o11y.span("UserExists") {
            set("db.system", "postgresql")
            set("db.user.name", username)
            queryBool("SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)", username)
        }

    override suspend fun databaseExists(dbName: String): Boolean =
        o11y.span("DatabaseExists") {
            set("db.system", "postgresql")
            set("db.name", dbName)
            queryBool("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)", dbName)
        }

    override suspend fun userCanAccessDatabase(
        username: String,
        dbName: String,
    ): Boolean =
        o11y.span("UserCanAccessDatabase") {
            set("db.system", "postgresql")
            set("db.user.name", username)
            set("db.name", dbName)
            queryBool("SELECT has_database_privilege(?, ?, 'CONNECT')", username, dbName)
        }

    override suspend fun grantUserAccessToTable(
        username: String,
        schema: String,
        table: String,
        privilege: String,
    ): Unit =
        o11y.span("GrantUserAccessToTable") {
            set("db.system", "postgresql")
            set("db.user.name", username)
            set("db.schema", schema)
            set("db.table", table)
            set("db.privilege", privilege)
            if (!Privilege.isValid(privilege)) {
                // The span scope records and rethrows this as the operation's failure.
                throw newError("invalid privilege: $privilege")
            }
            execute(grantSql(username, schema, table, privilege))
        }

    private suspend fun execute(sql: String): Unit =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.createStatement().use { it.execute(sql) }
            }
        }

    private suspend fun queryBool(
        sql: String,
        vararg args: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    args.forEachIndexed { idx, arg -> stmt.setString(idx + 1, arg) }
                    stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
                }
            }
        }

    internal companion object {
        /** Component name for this manager's Observer, mirroring `ExposedDatabaseClient.NAME`. */
        const val NAME: String = "table_access_manager"
    }
}

/**
 * Double-quotes a Postgres identifier, doubling embedded double-quotes per the SQL spec. Port of Go's
 * `quoteIdent`.
 */
internal fun quoteIdent(id: String): String = "\"" + id.replace("\"", "\"\"") + "\""

/**
 * Single-quotes a Postgres string literal, doubling embedded single-quotes. Safe under
 * `standard_conforming_strings=on` (the default since 9.1), where a backslash is an ordinary
 * character. Port of Go's `quoteLiteral`.
 */
internal fun quoteLiteral(value: String): String = "'" + value.replace("'", "''") + "'"

internal fun createUserSql(
    username: String,
    password: String,
): String = "CREATE USER ${quoteIdent(username)} WITH PASSWORD ${quoteLiteral(password)}"

internal fun deleteUserSql(username: String): String = "DROP USER IF EXISTS ${quoteIdent(username)}"

internal fun createDatabaseSql(
    dbName: String,
    owner: String,
): String = "CREATE DATABASE ${quoteIdent(dbName)} OWNER ${quoteIdent(owner)}"

internal fun deleteDatabaseSql(dbName: String): String = "DROP DATABASE IF EXISTS ${quoteIdent(dbName)}"

internal fun grantSql(
    username: String,
    schema: String,
    table: String,
    privilege: String,
): String = "GRANT $privilege ON TABLE ${quoteIdent(schema)}.${quoteIdent(table)} TO ${quoteIdent(username)}"
