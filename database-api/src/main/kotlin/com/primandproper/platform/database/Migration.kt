package com.primandproper.platform.database

import javax.sql.DataSource
import kotlin.time.Duration

/**
 * Runs database migrations. Port of platform-go's `database.Migrator`. Implementations wrap a concrete
 * migration engine (Go names darwin/goose); this is the seam the database client calls after
 * connecting when migrations are enabled.
 *
 * Coroutine-native (Go threads a `context.Context`) and takes the JDBC [javax.sql.DataSource] that is
 * the JVM analog of Go's `*sql.DB`.
 */
public interface Migrator {
    /** Applies any outstanding migrations against [dataSource]. */
    public suspend fun migrate(dataSource: DataSource)
}

/**
 * The configuration a database client needs, kept as an interface so the concrete `DatabaseConfig`
 * can provide it without the client depending on the config type — the same decoupling platform-go's
 * `database.ClientConfig` (`database/migration.go`) achieves to avoid an import cycle.
 *
 * The getter methods apply their own zero-value fallbacks (Go's `GetMaxPingAttempts` returns 50 when
 * unset, `GetMaxIdleConns` returns 5, and so on), so callers never see an unconfigured zero.
 */
public interface ClientConfig {
    /** The read connection string, in the provider's native form. */
    public fun readConnectionString(): String

    /** The write connection string, in the provider's native form. */
    public fun writeConnectionString(): String

    /** How many times to retry a ping before declaring the database not ready. Defaults to 50. */
    public fun maxPingAttempts(): Long

    /** How long to wait between ping attempts. */
    public fun pingWaitPeriod(): Duration

    /** The maximum number of idle connections to keep in a pool. Defaults to 5. */
    public fun maxIdleConns(): Int

    /** The maximum number of open connections a pool may hold. Defaults to 7. */
    public fun maxOpenConns(): Int

    /** How long a connection may be reused before being retired. Defaults to 30 minutes. */
    public fun connMaxLifetime(): Duration

    /**
     * Whether raw SQL text should be recorded on database spans. Go exposes this via an optional
     * interface assertion consumed by the backends; it is part of the contract here so the Exposed
     * backend can gate query-text capture the same way. Defaults to `false` (do not leak SQL).
     */
    public fun logQueries(): Boolean
}
