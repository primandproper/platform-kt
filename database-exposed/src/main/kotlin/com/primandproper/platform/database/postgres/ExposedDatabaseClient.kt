package com.primandproper.platform.database.postgres

import com.primandproper.platform.database.DatabaseClient
import com.primandproper.platform.database.Migrator
import com.primandproper.platform.database.SqlQueryExecutor
import com.primandproper.platform.database.SqlQueryExecutorAndTransactionManager
import com.primandproper.platform.database.config.DatabaseConfig
import com.primandproper.platform.database.config.DatabaseProvider
import com.primandproper.platform.errors.joinErrors
import com.primandproper.platform.errors.newError
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.observability.spanBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.postgresql.ds.PGSimpleDataSource
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Postgres [DatabaseClient], backed by JetBrains Exposed over JDBC. Port of platform-go's
 * `database/postgres.Client`.
 *
 * The abstraction is faithful; the implementation is fresh. Where Go builds a `*sql.DB` via `otelsql`
 * and pgx, this connects an Exposed [Database] over the JDBC [javax.sql.DataSource] the api contract
 * exposes. Read and write pools are separate objects, collapsing to one when only a single side is
 * configured — the same fallback the Go client applies.
 *
 * Every connection lifecycle event is instrumented through an [Observer] span exactly where the Go
 * client calls `o11y.Begin` — [isReady]/the ping loop, [rollbackTransaction], and construction (via
 * [postgresDatabaseClient]).
 *
 * TODO(metrics): Go registers `otelsql` DB-stats metrics and `db.sql.*` latency histograms through a
 * metrics provider. There is no metrics pillar in platform-kt's observability-api yet, so that is a
 * documented seam (the same descope `:cache-api` and `:circuitbreaking` make).
 */
public class ExposedDatabaseClient internal constructor(
    override val readDataSource: DataSource,
    override val writeDataSource: DataSource,
    private val maxPingAttempts: Int,
    private val pingWaitPeriod: Duration,
    private val o11y: Observer,
    private val timeFunc: () -> Instant,
) : DatabaseClient {
    /**
     * @param readDataSource the pool reads go to.
     * @param writeDataSource the pool writes go to; pass the same object as [readDataSource] for a
     *   single-pool client.
     * @param maxPingAttempts how many times [isReady] retries a ping; defaults to 50, matching the config default.
     * @param pingWaitPeriod how long [isReady] waits between attempts.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param timeFunc overridable clock for [currentTime]; defaults to [Instant.now].
     */
    public constructor(
        readDataSource: DataSource,
        writeDataSource: DataSource,
        maxPingAttempts: Int = DEFAULT_MAX_PING_ATTEMPTS,
        pingWaitPeriod: Duration = 1.seconds,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
        timeFunc: () -> Instant = { Instant.now() },
    ) : this(
        readDataSource,
        writeDataSource,
        maxPingAttempts,
        pingWaitPeriod,
        Observer(NAME, logger, tracerProvider),
        timeFunc,
    )

    /** The Exposed [Database] over [readDataSource]. Lazy — building it does not open a connection. */
    public val readDatabase: Database = Database.connect(readDataSource)

    /** The Exposed [Database] over [writeDataSource]; the same instance as [readDatabase] for a single-pool client. */
    public val writeDatabase: Database =
        if (writeDataSource === readDataSource) readDatabase else Database.connect(writeDataSource)

    override fun currentTime(): Instant = timeFunc()

    /**
     * A [JdbcSqlQueryExecutor] over the read pool for the redesigned Flow/Row query surface. Reuses this
     * client's observer, so its `Query`/`QueryOne`/`Exec`/`WithPrepared` spans nest under the caller's.
     */
    public fun readExecutor(logQueries: Boolean = false): SqlQueryExecutor = JdbcSqlQueryExecutor(readDataSource, o11y, logQueries)

    /** A [JdbcSqlQueryExecutor] over the write pool; see [readExecutor]. */
    public fun writeExecutor(logQueries: Boolean = false): SqlQueryExecutor = JdbcSqlQueryExecutor(writeDataSource, o11y, logQueries)

    /**
     * Reports whether both pools answer a ping, retrying up to [maxPingAttempts] with [pingWaitPeriod]
     * between attempts. Port of Go's `IsReady`/`waitForPing` — reads first, then writes when the write
     * pool differs.
     */
    public suspend fun isReady(): Boolean =
        o11y.span("IsReady") {
            set("db.system", "postgresql")
            set("db.ping.max_attempts", maxPingAttempts.toLong())

            if (!waitForPing(readDataSource, "read")) {
                return@span false
            }
            if (writeDataSource === readDataSource) {
                return@span true
            }
            waitForPing(writeDataSource, "write")
        }

    private suspend fun waitForPing(
        dataSource: DataSource,
        connectionName: String,
    ): Boolean {
        val attempts = maxPingAttempts
        for (attempt in 0 until attempts) {
            val failure = pingOnce(dataSource) ?: return true

            // Attach the failure cause so a persistently-refused pool is diagnosable, instead of the
            // log only saying a ping "failed" with the reason dropped.
            o11y.logger.withValue("connection", connectionName).withValue("attempt_count", attempt)
                .withError(failure)
                .info("ping failed, waiting for db")

            // Don't sleep after the final attempt.
            if (attempt == attempts - 1) {
                break
            }
            delay(pingWaitPeriod)
        }
        return false
    }

    /** One ping attempt: `null` means the pool answered; a non-null [Throwable] is why it did not. */
    private suspend fun pingOnce(dataSource: DataSource): Throwable? =
        withContext(Dispatchers.IO) {
            try {
                if (dataSource.connection.use { it.isValid(PING_TIMEOUT_SECONDS) }) {
                    null
                } else {
                    newError("database connection reported not valid")
                }
            } catch (e: Exception) {
                e
            }
        }

    override suspend fun rollbackTransaction(tx: SqlQueryExecutorAndTransactionManager) {
        o11y.span("RollbackTransaction") {
            logger.debug("rolling back transaction")
            try {
                tx.rollback()
            } catch (e: Exception) {
                // Fire-and-forget: record the failure but do not rethrow, matching Go's Acknowledge.
                acknowledge(e, "rolling back transaction")
            }
            logger.debug("transaction rolled back")
        }
    }

    /**
     * Closes whichever pools are [AutoCloseable] (a JDBC connection pool typically is; a bare
     * [javax.sql.DataSource] like H2's is not). Always attempts the write pool even if the read pool
     * failed to close, so a read-close error cannot leak the write connection — the same guarantee as
     * Go's `Close`.
     */
    override fun close() {
        val errors = mutableListOf<Throwable>()
        if (writeDataSource !== readDataSource) {
            closeIfPossible(writeDataSource, "write", errors)
        }
        closeIfPossible(readDataSource, "read", errors)
        joinErrors(*errors.toTypedArray())?.let { throw it }
    }

    private fun closeIfPossible(
        dataSource: DataSource,
        name: String,
        errors: MutableList<Throwable>,
    ) {
        val closeable = dataSource as? AutoCloseable ?: return
        try {
            closeable.close()
        } catch (e: Exception) {
            o11y.logger.error("closing $name database connection", e)
            errors += e
        }
    }

    internal companion object {
        const val NAME: String = "db_client"
        const val DEFAULT_MAX_PING_ATTEMPTS: Int = 50
        const val PING_TIMEOUT_SECONDS: Int = 1
    }
}

/**
 * Builds a Postgres [DatabaseClient] from [config]. Port of platform-go's `postgres.ProvideDatabaseClient`.
 *
 * Read and write [PGSimpleDataSource]s are built from the config's connection details and connect
 * lazily, so this never blocks on — or requires — a reachable server. When only one side is
 * configured it is used for both; when neither is, this throws. Instrumented with a
 * `ProvideDatabaseClient` span recording `db.system` and whether each side was configured.
 */
public fun postgresDatabaseClient(
    config: DatabaseConfig,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): DatabaseClient {
    val o11y = Observer(ExposedDatabaseClient.NAME, logger, tracerProvider)
    return o11y.spanBlocking("ProvideDatabaseClient") {
        val writeConnection = config.writeConnection
        val readConfigured = config.readConnection.host.isNotBlank()
        val writeConfigured = writeConnection != null && writeConnection.host.isNotBlank()

        set("db.system", "postgresql")
        set("db.read_configured", readConfigured)
        set("db.write_configured", writeConfigured)

        var readDs: DataSource? = if (readConfigured) buildPostgresDataSource(config, config.readConnection) else null
        var writeDs: DataSource? = if (writeConfigured) buildPostgresDataSource(config, writeConnection!!) else null

        if (readDs == null && writeDs == null) {
            throw newError("at least one of read or write connection must be provided")
        }
        if (readDs == null) readDs = writeDs
        if (writeDs == null) writeDs = readDs

        ExposedDatabaseClient(
            readDataSource = readDs!!,
            writeDataSource = writeDs!!,
            maxPingAttempts = config.maxPingAttempts(),
            pingWaitPeriod = config.pingWaitPeriod(),
            logger = logger,
            tracerProvider = tracerProvider,
        )
    }
}

private fun buildPostgresDataSource(
    config: DatabaseConfig,
    connection: com.primandproper.platform.database.config.ConnectionDetails,
): DataSource =
    PGSimpleDataSource().apply {
        serverNames = arrayOf(connection.host)
        portNumbers = intArrayOf(connection.port)
        user = connection.username
        password = connection.password
        databaseName = connection.database
        sslMode = connection.sslMode()
        // otelsql's connection-lifetime tuning has no PGSimpleDataSource analog; connection pooling
        // (max idle/open, lifetime) belongs to a pooling DataSource layered on top — see TODO(pooling).
        connectTimeout = config.pingWaitPeriod().inWholeSeconds.toInt().coerceAtLeast(1)
    }

/**
 * Dispatches to the backend for the configured provider and, when enabled, runs migrations. Port of
 * platform-go's `databasecfg.ProvideDatabase`.
 *
 * Only Postgres is implemented in this module. MySQL and SQLite are named seams:
 *  - TODO(mysql): a JDBC/Exposed MySQL backend (`com.mysql:mysql-connector-j`).
 *  - TODO(sqlite): a JDBC/Exposed SQLite backend (`org.xerial:sqlite-jdbc`).
 *  - TODO(r2dbc): Go is blocking JDBC throughout; a fully non-blocking client would swap this for an
 *    R2DBC driver behind the same suspend contract.
 */
public suspend fun DatabaseClient(
    config: DatabaseConfig,
    migrator: Migrator? = null,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): DatabaseClient {
    val client =
        when (config.provider) {
            DatabaseProvider.POSTGRES -> postgresDatabaseClient(config, logger, tracerProvider)
            DatabaseProvider.MYSQL -> throw newError("mysql provider is not yet ported; TODO(mysql)")
            DatabaseProvider.SQLITE -> throw newError("sqlite provider is not yet ported; TODO(sqlite)")
        }

    if (config.runMigrations && migrator != null) {
        migrator.migrate(client.writeDataSource)
    }

    return client
}
