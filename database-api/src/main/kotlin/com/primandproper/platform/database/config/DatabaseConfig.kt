package com.primandproper.platform.database.config

import com.primandproper.platform.database.ClientConfig
import com.primandproper.platform.errors.newError
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Provider names, ported from platform-go's `databasecfg.ProviderPostgres`/`ProviderMySQL`/
 * `ProviderSQLite` constants. Kept as plain strings (not an enum) because Go's `Config.Provider` is a
 * free-form string — a service may set an unknown provider and have validation reject it.
 */
public object DatabaseProviders {
    public const val POSTGRES: String = "postgres"
    public const val MYSQL: String = "mysql"
    public const val SQLITE: String = "sqlite"
}

/**
 * A single database connection's coordinates. Port of platform-go's `databasecfg.ConnectionDetails`.
 *
 * Go mutates a `*ConnectionDetails` in place (e.g. `LoadFromURL`); this port is an immutable data
 * class, so the URL loader is the [fromUrl] factory instead of a mutating method.
 */
public data class ConnectionDetails(
    val username: String = "",
    val password: String = "",
    val database: String = "",
    val host: String = "",
    val port: Int = 0,
    val disableSsl: Boolean = false,
) {
    /**
     * The libpq `sslmode` for this connection. Mirrors Go: `disable` when SSL is turned off, otherwise
     * `prefer` (pgx's own default — encrypt if offered, else fall back), so `disableSsl` actually takes
     * effect without changing behavior for existing deployments.
     */
    public fun sslMode(): String = if (disableSsl) "disable" else "prefer"

    /**
     * The libpq keyword/value connection string (Go's `String()`), the native form pgx/JDBC-Postgres
     * accept. Every value is single-quoted and backslash-escaped via [quotePgConnValue] so a value
     * containing a space, quote, or `key=value`-looking payload cannot inject extra parameters.
     */
    public fun postgresConnectionString(): String =
        listOf(
            "user=" + quotePgConnValue(username),
            "password=" + quotePgConnValue(password),
            "database=" + quotePgConnValue(database),
            "host=" + quotePgConnValue(host),
            "port=$port",
            "sslmode=" + sslMode(),
        ).joinToString(" ")

    /**
     * A `postgres://` URI form (Go's `URI()`), building it through [java.net.URI] so credentials with
     * special characters are percent-encoded rather than concatenated.
     */
    public fun uri(): String =
        URI(
            "postgres",
            "$username:$password",
            host,
            port,
            "/$database",
            "sslmode=" + sslMode(),
            null,
        ).toString()

    /**
     * A MySQL DSN (Go's `MySQLDSN()`). `parseTime=true` is required so the driver scans
     * `DATETIME`/`TIMESTAMP` into a time type rather than raw bytes.
     *
     * TODO(mysql): platform-go assembles this through the MySQL driver's own `Config.FormatDSN`, which
     * escapes credentials. This port emits the standard `user:pass@tcp(host:port)/db` form directly;
     * hardening the escaping belongs with the MySQL backend when it lands (see the `mysql` seam in
     * `:database-exposed`).
     */
    public fun mysqlDsn(): String = "$username:$password@tcp($host:$port)/$database?parseTime=true"

    /** The SQLite database file path (Go's `SQLiteDSN()`). */
    public fun sqliteDsn(): String = database

    /**
     * Validates that every field a networked provider requires is present. Port of Go's ozzo
     * `ValidateWithContext`, which requires host, database, username, password, and a non-zero port.
     * Throws PlatformException describing the first missing field.
     */
    public fun validate() {
        val missing =
            buildList {
                if (host.isBlank()) add("host")
                if (database.isBlank()) add("database")
                if (username.isBlank()) add("username")
                if (password.isBlank()) add("password")
                if (port == 0) add("port")
            }
        if (missing.isNotEmpty()) {
            throw newError("invalid connection details: missing ${missing.joinToString(", ")}")
        }
    }

    public companion object {
        /**
         * Parses a Postgres connection URL into a [ConnectionDetails]. Port of Go's `LoadFromURL`.
         * Throws PlatformException when the URL is malformed or is missing a numeric port (matching
         * Go returning an error for those cases).
         */
        public fun fromUrl(url: String): ConnectionDetails {
            val uri =
                try {
                    URI(url)
                } catch (e: URISyntaxException) {
                    throw newError("parsing connection URL: ${e.message}")
                }

            val host = uri.host ?: throw newError("connection URL is missing a host")
            val port = uri.port
            if (port == -1) throw newError("connection URL is missing a valid port")

            val userInfo = uri.userInfo.orEmpty()
            val sep = userInfo.indexOf(':')
            val username = if (sep >= 0) userInfo.substring(0, sep) else userInfo
            val password = if (sep >= 0) userInfo.substring(sep + 1) else ""

            return ConnectionDetails(
                username = username,
                password = password,
                host = host,
                port = port,
                database = uri.path.removePrefix("/"),
                disableSsl = queryParam(uri.query, "sslmode") == "disable",
            )
        }

        private fun queryParam(
            query: String?,
            key: String,
        ): String? =
            query
                ?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it.first() == key }
                ?.getOrNull(1)
    }
}

/**
 * Single-quotes a libpq value, backslash-escaping embedded backslashes and single quotes (in that
 * order, so the escape added for a quote is not itself doubled). Port of Go's `quotePGConnValue`.
 */
internal fun quotePgConnValue(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

/**
 * The database configuration. Port of platform-go's `databasecfg.Config` (the portable subset — the
 * `Encryption` sub-config, which belongs to `:cryptography`, is intentionally omitted).
 *
 * Implements [ClientConfig] so it can be handed straight to a database client. The getter methods
 * apply the same zero-value fallbacks Go's getters do (50 ping attempts, 5 idle / 7 open connections,
 * 30-minute connection lifetime), so an unset field never leaks a bare zero to a client.
 *
 * Immutable: [ensureDefaults] returns a normalized copy rather than mutating in place (Go's
 * `EnsureDefaults` mutates the receiver).
 */
public data class DatabaseConfig(
    val provider: String = DatabaseProviders.POSTGRES,
    val readConnection: ConnectionDetails = ConnectionDetails(),
    val writeConnection: ConnectionDetails = ConnectionDetails(),
    val pingWaitPeriod: Duration = 1.seconds,
    val maxPingAttempts: Long = 0,
    val connMaxLifetime: Duration = Duration.ZERO,
    val maxIdleConns: Int = 0,
    val maxOpenConns: Int = 0,
    val debug: Boolean = false,
    val logQueries: Boolean = false,
    val runMigrations: Boolean = false,
    val enableDatabaseMetrics: Boolean = false,
) : ClientConfig {
    private fun normalizedProvider(): String = provider.trim().lowercase()

    /** The read connection string in the provider's native form. Implements [ClientConfig.readConnectionString]. */
    override fun readConnectionString(): String = connectionStringForProvider(readConnection)

    /** The write connection string in the provider's native form. Implements [ClientConfig.writeConnectionString]. */
    override fun writeConnectionString(): String = connectionStringForProvider(writeConnection)

    private fun connectionStringForProvider(cd: ConnectionDetails): String =
        when (normalizedProvider()) {
            DatabaseProviders.MYSQL -> cd.mysqlDsn()
            DatabaseProviders.SQLITE -> cd.sqliteDsn()
            else -> cd.postgresConnectionString()
        }

    /** Returns [maxPingAttempts], or 50 when unset, so a client retries rather than pinging once. */
    override fun maxPingAttempts(): Long = if (maxPingAttempts == 0L) DEFAULT_MAX_PING_ATTEMPTS else maxPingAttempts

    override fun pingWaitPeriod(): Duration = pingWaitPeriod

    /** Returns [maxIdleConns], or 5 when unset. */
    override fun maxIdleConns(): Int = if (maxIdleConns == 0) DEFAULT_MAX_IDLE_CONNS else maxIdleConns

    /** Returns [maxOpenConns], or 7 when unset. */
    override fun maxOpenConns(): Int = if (maxOpenConns == 0) DEFAULT_MAX_OPEN_CONNS else maxOpenConns

    /** Returns [connMaxLifetime], or 30 minutes when unset or non-positive. */
    override fun connMaxLifetime(): Duration = if (connMaxLifetime.isPositive()) connMaxLifetime else DEFAULT_CONN_MAX_LIFETIME

    override fun logQueries(): Boolean = logQueries

    /**
     * The JDBC/driver name for the configured provider (Go's `driverName`): `mysql`, `sqlite`, or `pgx`
     * for Postgres and any unknown provider.
     */
    public fun driverName(): String =
        when (normalizedProvider()) {
            DatabaseProviders.MYSQL -> "mysql"
            DatabaseProviders.SQLITE -> "sqlite"
            else -> "pgx"
        }

    /** Returns a copy with sensible defaults filled in for zero-valued fields. Port of Go's `EnsureDefaults`. */
    public fun ensureDefaults(): DatabaseConfig =
        copy(
            provider = provider.ifBlank { DatabaseProviders.POSTGRES },
            pingWaitPeriod = if (pingWaitPeriod == Duration.ZERO) 1.seconds else pingWaitPeriod,
            connMaxLifetime = if (connMaxLifetime == Duration.ZERO) DEFAULT_CONN_MAX_LIFETIME else connMaxLifetime,
            maxIdleConns = if (maxIdleConns == 0) DEFAULT_MAX_IDLE_CONNS else maxIdleConns,
            maxOpenConns = if (maxOpenConns == 0) DEFAULT_MAX_OPEN_CONNS else maxOpenConns,
            maxPingAttempts = if (maxPingAttempts == 0L) DEFAULT_MAX_PING_ATTEMPTS else maxPingAttempts,
        )

    /**
     * Validates the config, throwing PlatformException on failure. Provider-aware, mirroring Go:
     * SQLite only needs a database file path on either connection, while every other provider requires
     * a fully specified read connection; a supplied write connection is validated regardless.
     */
    public fun validate() {
        if (normalizedProvider() == DatabaseProviders.SQLITE) {
            if (readConnection.database.isBlank() && writeConnection.database.isBlank()) {
                throw newError("sqlite requires a database file path on the read or write connection")
            }
            return
        }

        readConnection.validate()

        if (writeConnection != ConnectionDetails()) {
            writeConnection.validate()
        }
    }

    public companion object {
        private const val DEFAULT_MAX_PING_ATTEMPTS = 50L
        private const val DEFAULT_MAX_IDLE_CONNS = 5
        private const val DEFAULT_MAX_OPEN_CONNS = 7
        private val DEFAULT_CONN_MAX_LIFETIME = 30.minutes
    }
}
