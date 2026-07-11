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
 * The resolved configuration a database client needs to connect. Redesigned from platform-go's
 * getter-method `database.ClientConfig` (`database/migration.go`) into an idiomatic immutable value
 * (see P3-3): a data class of `val` properties instead of an interface of getter functions, with the
 * zero-value fallbacks already applied by whoever builds it (see `DatabaseConfig.toClientConfig`), so
 * a client never sees an unconfigured zero and never re-derives a default.
 *
 * Retry counts are [Int] (JDBC/loop counters, not Go's `int64`); delays are [kotlin.time.Duration].
 *
 * @property readConnectionString the read connection string, in the provider's native form.
 * @property writeConnectionString the write connection string, in the provider's native form.
 * @property maxPingAttempts how many times to retry a ping before declaring the database not ready.
 * @property pingWaitPeriod how long to wait between ping attempts.
 * @property maxIdleConns the maximum number of idle connections to keep in a pool.
 * @property maxOpenConns the maximum number of open connections a pool may hold.
 * @property connMaxLifetime how long a connection may be reused before being retired.
 * @property logQueries whether raw SQL text may be recorded on database spans (default `false`: do not leak SQL).
 */
public data class ClientConfig(
    val readConnectionString: String,
    val writeConnectionString: String,
    val maxPingAttempts: Int,
    val pingWaitPeriod: Duration,
    val maxIdleConns: Int,
    val maxOpenConns: Int,
    val connMaxLifetime: Duration,
    val logQueries: Boolean = false,
)
