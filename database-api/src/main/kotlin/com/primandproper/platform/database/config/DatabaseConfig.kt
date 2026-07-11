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
 * `ProviderSQLite` constants, modelled as an enum so an unknown provider is rejected the way Go's
 * validation rejects it. [value] is the wire/string form validated against configuration.
 *
 * Go's `Config.Provider` is a free-form string that a service may set to an unknown value; this port
 * turns the raw string into the enum once, at the parse edge ([DatabaseConfig.providerFromValue] /
 * [fromValue]), where a blank value resolves to [POSTGRES] and an unknown one fails loudly (the P2-36
 * loud-failure contract) — so everything downstream consumes the typed value.
 */
public enum class DatabaseProvider(
    public val value: String,
) {
    POSTGRES("postgres"),
    MYSQL("mysql"),
    SQLITE("sqlite"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider. A blank value resolves to `null` here; the module's blank→[POSTGRES]
         * default is applied by [DatabaseConfig.providerFromValue].
         */
        public fun fromValue(value: String): DatabaseProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
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
 * Immutable, with the constructor's default arguments supplying every field's default. Its resolving
 * getter methods are the single source of the zero-value fallbacks Go's getters apply (50 ping
 * attempts, 5 idle / 7 open connections, 30-minute connection lifetime), so an unset field never leaks
 * a bare zero; [toClientConfig] bundles the resolved values into the immutable [ClientConfig] a client
 * consumes. A `null` [writeConnection] (the default) means no separate write connection is configured.
 */
public data class DatabaseConfig(
    val provider: DatabaseProvider = DatabaseProvider.POSTGRES,
    val readConnection: ConnectionDetails = ConnectionDetails(),
    val writeConnection: ConnectionDetails? = null,
    val pingWaitPeriod: Duration = 1.seconds,
    val maxPingAttempts: Int = 0,
    val connMaxLifetime: Duration = Duration.ZERO,
    val maxIdleConns: Int = 0,
    val maxOpenConns: Int = 0,
    val debug: Boolean = false,
    val logQueries: Boolean = false,
    val runMigrations: Boolean = false,
    val enableDatabaseMetrics: Boolean = false,
) {
    /** The read connection string in the provider's native form. */
    public fun readConnectionString(): String = connectionStringForProvider(readConnection)

    /** The write connection string in the provider's native form, or `""` when no write connection is set. */
    public fun writeConnectionString(): String = writeConnection?.let(::connectionStringForProvider) ?: ""

    private fun connectionStringForProvider(cd: ConnectionDetails): String =
        when (provider) {
            DatabaseProvider.MYSQL -> cd.mysqlDsn()
            DatabaseProvider.SQLITE -> cd.sqliteDsn()
            DatabaseProvider.POSTGRES -> cd.postgresConnectionString()
        }

    /** Returns [maxPingAttempts], or 50 when unset, so a client retries rather than pinging once. */
    public fun maxPingAttempts(): Int = if (maxPingAttempts == 0) DEFAULT_MAX_PING_ATTEMPTS else maxPingAttempts

    /** Returns [pingWaitPeriod]. */
    public fun pingWaitPeriod(): Duration = pingWaitPeriod

    /** Returns [maxIdleConns], or 5 when unset. */
    public fun maxIdleConns(): Int = if (maxIdleConns == 0) DEFAULT_MAX_IDLE_CONNS else maxIdleConns

    /** Returns [maxOpenConns], or 7 when unset. */
    public fun maxOpenConns(): Int = if (maxOpenConns == 0) DEFAULT_MAX_OPEN_CONNS else maxOpenConns

    /** Returns [connMaxLifetime], or 30 minutes when unset or non-positive. */
    public fun connMaxLifetime(): Duration = if (connMaxLifetime.isPositive()) connMaxLifetime else DEFAULT_CONN_MAX_LIFETIME

    /** Returns [logQueries]. */
    public fun logQueries(): Boolean = logQueries

    /**
     * Bundles the resolved (fallback-applied) settings into the immutable [ClientConfig] a database
     * client consumes, so the client never re-derives a default. Connection strings are rendered in the
     * configured provider's native form.
     */
    public fun toClientConfig(): ClientConfig =
        ClientConfig(
            readConnectionString = readConnectionString(),
            writeConnectionString = writeConnectionString(),
            maxPingAttempts = maxPingAttempts(),
            pingWaitPeriod = pingWaitPeriod(),
            maxIdleConns = maxIdleConns(),
            maxOpenConns = maxOpenConns(),
            connMaxLifetime = connMaxLifetime(),
            logQueries = logQueries(),
        )

    /**
     * The JDBC/driver name for the configured provider (Go's `driverName`): `mysql`, `sqlite`, or `pgx`
     * for Postgres.
     */
    public fun driverName(): String =
        when (provider) {
            DatabaseProvider.MYSQL -> "mysql"
            DatabaseProvider.SQLITE -> "sqlite"
            DatabaseProvider.POSTGRES -> "pgx"
        }

    /**
     * Validates the config, throwing PlatformException on failure. Provider-aware, mirroring Go:
     * SQLite only needs a database file path on either connection, while every other provider requires
     * a fully specified read connection; a supplied write connection is validated regardless.
     *
     * The unknown-provider rejection now lives at the parse edge ([providerFromValue]): [provider] is
     * already a typed [DatabaseProvider], so an unrecognized name can never reach here.
     */
    public fun validate() {
        if (provider == DatabaseProvider.SQLITE) {
            if (readConnection.database.isBlank() && writeConnection?.database.isNullOrBlank()) {
                throw newError("sqlite requires a database file path on the read or write connection")
            }
            return
        }

        readConnection.validate()

        writeConnection?.validate()
    }

    public companion object {
        private const val DEFAULT_MAX_PING_ATTEMPTS = 50
        private const val DEFAULT_MAX_IDLE_CONNS = 5
        private const val DEFAULT_MAX_OPEN_CONNS = 7
        private val DEFAULT_CONN_MAX_LIFETIME = 30.minutes

        /**
         * The parse edge: resolves a raw provider string (e.g. from config) to a [DatabaseProvider],
         * treating a blank value as [DatabaseProvider.POSTGRES] (the default) and rejecting a non-blank
         * unknown name loudly (the P2-36 loud-failure contract), the same discipline as
         * `SecretsConfig.providerFromValue`.
         *
         * @throws com.primandproper.platform.errors.PlatformException for a non-blank unknown provider.
         */
        public fun providerFromValue(value: String): DatabaseProvider =
            if (value.isBlank()) {
                DatabaseProvider.POSTGRES
            } else {
                DatabaseProvider.fromValue(value) ?: throw newError("unknown database provider: \"$value\"")
            }
    }
}
