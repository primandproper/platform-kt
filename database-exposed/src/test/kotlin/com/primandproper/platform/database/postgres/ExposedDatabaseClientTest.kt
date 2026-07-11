package com.primandproper.platform.database.postgres

import com.primandproper.platform.database.Migrator
import com.primandproper.platform.database.Row
import com.primandproper.platform.database.SqlQueryExecutorAndTransactionManager
import com.primandproper.platform.database.SqlResult
import com.primandproper.platform.database.config.ConnectionDetails
import com.primandproper.platform.database.config.DatabaseConfig
import com.primandproper.platform.database.config.DatabaseProvider
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import org.postgresql.ds.PGSimpleDataSource
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Port of platform-go's `database/postgres/postgres_test.go` and the `ProvideDatabase` cases of
 * `database/config/config_test.go`, exercised against an in-memory H2 database and lazily-connecting
 * Postgres data sources — no live Postgres required.
 */
class ExposedDatabaseClientTest {
    private val dbCounter = AtomicInteger(0)

    private fun h2(): DataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:client-test-${dbCounter.incrementAndGet()};DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }

    private val postgresConnection =
        ConnectionDetails(host = "localhost", port = 5432, username = "user", password = "pass", database = "db")

    @Test
    fun `isReady returns true for a reachable database`() =
        runTest {
            val ds = h2()
            val client = ExposedDatabaseClient(ds, ds, maxPingAttempts = 3, pingWaitPeriod = 1.seconds)
            assertTrue(client.isReady())
        }

    @Test
    fun `currentTime uses the injected clock`() {
        val fixed = Instant.parse("2026-07-07T12:00:00Z")
        val ds = h2()
        val client = ExposedDatabaseClient(ds, ds, timeFunc = { fixed })
        assertEquals(fixed, client.currentTime())
    }

    @Test
    fun `a single pool shares one Exposed Database`() {
        val ds = h2()
        val client = ExposedDatabaseClient(ds, ds)
        assertSame(client.readDatabase, client.writeDatabase)
    }

    @Test
    fun `separate pools build separate Exposed Databases`() {
        val client = ExposedDatabaseClient(h2(), h2())
        assertTrue(client.readDatabase !== client.writeDatabase)
    }

    @Test
    fun `rollbackTransaction rolls back and observes the operation`() =
        runTest {
            val obs = RecordingObserver()
            val ds = h2()
            val client = ExposedDatabaseClient(ds, ds, 3, 1.seconds, obs) { Instant.now() }
            val tx = FakeTransaction()

            client.rollbackTransaction(tx)

            assertTrue(tx.rolledBack)
            val op = obs.operations.last { it.name == "RollbackTransaction" }
            assertTrue(op.ended)
            assertTrue(op.errors.isEmpty())
        }

    @Test
    fun `rollbackTransaction acknowledges a failing rollback without throwing`() =
        runTest {
            val obs = RecordingObserver()
            val ds = h2()
            val client = ExposedDatabaseClient(ds, ds, 3, 1.seconds, obs) { Instant.now() }

            client.rollbackTransaction(FakeTransaction(failOnRollback = true))

            val op = obs.operations.last { it.name == "RollbackTransaction" }
            assertTrue(op.errors.isNotEmpty())
            assertTrue(op.ended)
        }

    @Test
    fun `close does not throw for a plain DataSource`() {
        val ds = h2()
        ExposedDatabaseClient(ds, ds).close()
    }

    @Test
    fun `postgresDatabaseClient builds a lazily-connecting postgres client`() {
        val config =
            DatabaseConfig(
                provider = DatabaseProvider.POSTGRES,
                readConnection = postgresConnection,
                writeConnection = postgresConnection,
            )
        val client = postgresDatabaseClient(config)
        assertNotNull(client.readDataSource)
        assertTrue(client.readDataSource is PGSimpleDataSource)
    }

    @Test
    fun `postgresDatabaseClient throws when no connection is configured`() {
        assertFailsWith<PlatformException> {
            postgresDatabaseClient(DatabaseConfig(provider = DatabaseProvider.POSTGRES))
        }
    }

    @Test
    fun `providerFromValue rejects an invalid provider at the parse edge`() {
        val e =
            assertFailsWith<PlatformException> {
                DatabaseConfig.providerFromValue("invalid_provider")
            }
        assertTrue(e.message!!.contains("unknown database provider"))
    }

    @Test
    fun `DatabaseClient reports mysql and sqlite as named seams`() =
        runTest {
            assertFailsWith<PlatformException> {
                DatabaseClient(DatabaseConfig(provider = DatabaseProvider.MYSQL, readConnection = postgresConnection))
            }
            val sqlite =
                DatabaseConfig(provider = DatabaseProvider.SQLITE, readConnection = ConnectionDetails(database = "/tmp/x.db"))
            assertFailsWith<PlatformException> { DatabaseClient(sqlite) }
        }

    @Test
    fun `DatabaseClient runs migrations when enabled`() =
        runTest {
            val config =
                DatabaseConfig(
                    provider = DatabaseProvider.POSTGRES,
                    runMigrations = true,
                    readConnection = postgresConnection,
                    writeConnection = postgresConnection,
                )
            val migrator = FakeMigrator()
            DatabaseClient(config, migrator)
            assertTrue(migrator.called)
        }

    private class FakeMigrator : Migrator {
        var called = false

        override suspend fun migrate(dataSource: DataSource) {
            called = true
        }
    }

    /** A minimal transaction double; only [rollback] is meaningful, mirroring how the client uses it. */
    private class FakeTransaction(
        private val failOnRollback: Boolean = false,
    ) : SqlQueryExecutorAndTransactionManager {
        var rolledBack = false

        override suspend fun rollback() {
            if (failOnRollback) throw RuntimeException("rollback failed")
            rolledBack = true
        }

        override suspend fun exec(
            query: String,
            vararg args: Any?,
        ): SqlResult = throw UnsupportedOperationException()

        override fun query(
            query: String,
            vararg args: Any?,
        ): Flow<Row> = throw UnsupportedOperationException()

        override suspend fun queryOne(
            query: String,
            vararg args: Any?,
        ): Row = throw UnsupportedOperationException()

        override suspend fun <T> withPrepared(
            sql: String,
            block: suspend (com.primandproper.platform.database.PreparedHandle) -> T,
        ): T = throw UnsupportedOperationException()
    }
}
