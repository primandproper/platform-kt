package com.primandproper.platform.database.config

import com.primandproper.platform.errors.PlatformException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of platform-go's `database/config/config_test.go`. */
class DatabaseConfigTest {
    private val fullRead =
        ConnectionDetails(host = "localhost", username = "root", password = "password", port = 5432, database = "test")

    @Test
    fun `an omitted config resolves every default through its getters`() {
        val cfg = DatabaseConfig()

        assertEquals("pgx", DatabaseConfig(provider = DatabaseConfig.providerFromValue("")).driverName())
        assertEquals(1.seconds, cfg.pingWaitPeriod())
        assertEquals(30.minutes, cfg.connMaxLifetime())
        assertEquals(5, cfg.maxIdleConns())
        assertEquals(7, cfg.maxOpenConns())
        assertEquals(50, cfg.maxPingAttempts())
    }

    @Test
    fun `explicit values are passed through unchanged`() {
        val cfg =
            DatabaseConfig(
                provider = DatabaseProvider.MYSQL,
                pingWaitPeriod = 5.seconds,
                connMaxLifetime = 1.hours,
                maxIdleConns = 10,
                maxOpenConns = 20,
            )

        assertEquals(DatabaseProvider.MYSQL, cfg.provider)
        assertEquals(5.seconds, cfg.pingWaitPeriod())
        assertEquals(1.hours, cfg.connMaxLifetime())
        assertEquals(10, cfg.maxIdleConns())
        assertEquals(20, cfg.maxOpenConns())
    }

    @Test
    fun `GetReadConnectionString renders a postgres keyword string`() {
        val cfg = DatabaseConfig(readConnection = ConnectionDetails("user", "pass", "db", "localhost", 5432))
        assertEquals(
            "user='user' password='pass' database='db' host='localhost' port=5432 sslmode=prefer",
            cfg.readConnectionString(),
        )
    }

    @Test
    fun `GetWriteConnectionString renders a postgres keyword string`() {
        val cfg = DatabaseConfig(writeConnection = ConnectionDetails("writer", "secret", "mydb", "writehost", 5433))
        assertEquals(
            "user='writer' password='secret' database='mydb' host='writehost' port=5433 sslmode=prefer",
            cfg.writeConnectionString(),
        )
    }

    @Test
    fun `getters apply zero-value fallbacks and pass set values through`() {
        assertEquals(42, DatabaseConfig(maxPingAttempts = 42).maxPingAttempts())
        assertEquals(50, DatabaseConfig().maxPingAttempts())

        assertEquals(3.seconds, DatabaseConfig(pingWaitPeriod = 3.seconds).pingWaitPeriod())

        assertEquals(5, DatabaseConfig().maxIdleConns())
        assertEquals(12, DatabaseConfig(maxIdleConns = 12).maxIdleConns())

        assertEquals(7, DatabaseConfig().maxOpenConns())
        assertEquals(15, DatabaseConfig(maxOpenConns = 15).maxOpenConns())

        assertEquals(30.minutes, DatabaseConfig().connMaxLifetime())
        assertEquals(30.minutes, DatabaseConfig(connMaxLifetime = (-1).seconds).connMaxLifetime())
        assertEquals(1.hours, DatabaseConfig(connMaxLifetime = 1.hours).connMaxLifetime())
    }

    @Test
    fun `GetLogQueries defaults to false and returns the set value`() {
        assertFalse(DatabaseConfig().logQueries())
        assertTrue(DatabaseConfig(logQueries = true).logQueries())
    }

    @Test
    fun `ValidateWithContext accepts a full read connection`() {
        DatabaseConfig(readConnection = fullRead).validate()
    }

    @Test
    fun `ValidateWithContext accepts a full read and write connection`() {
        DatabaseConfig(readConnection = fullRead, writeConnection = fullRead).validate()
    }

    @Test
    fun `sqlite validates with only a database file path`() {
        DatabaseConfig(provider = DatabaseProvider.SQLITE, readConnection = ConnectionDetails(database = "/tmp/test.db")).validate()
    }

    @Test
    fun `sqlite requires a database file path`() {
        assertFailsWith<PlatformException> { DatabaseConfig(provider = DatabaseProvider.SQLITE).validate() }
    }

    @Test
    fun `rejects an incomplete write connection`() {
        assertFailsWith<PlatformException> {
            DatabaseConfig(readConnection = fullRead, writeConnection = ConnectionDetails(host = "writehost")).validate()
        }
    }

    @Test
    fun `providerFromValue rejects an unknown provider string`() {
        val ex =
            assertFailsWith<PlatformException> {
                DatabaseConfig.providerFromValue("cockroach")
            }
        assertTrue(ex.message!!.contains("cockroach"), "error should name the offending provider")
    }

    @Test
    fun `providerFromValue resolves the known providers and maps blank to postgres`() {
        assertEquals(DatabaseProvider.POSTGRES, DatabaseConfig.providerFromValue(""))
        assertEquals(DatabaseProvider.POSTGRES, DatabaseConfig.providerFromValue("postgres"))
        assertEquals(DatabaseProvider.MYSQL, DatabaseConfig.providerFromValue("  MYSQL  "))
        DatabaseConfig(provider = DatabaseProvider.POSTGRES, readConnection = fullRead).validate()
    }

    @Test
    fun `ConnectionDetails fromUrl parses a full postgres URL`() {
        val d = ConnectionDetails.fromUrl("postgres://dbuser:hunter2@pgdatabase:5432/database?sslmode=disable")
        assertEquals("dbuser", d.username)
        assertEquals("hunter2", d.password)
        assertEquals("pgdatabase", d.host)
        assertEquals(5432, d.port)
        assertEquals("database", d.database)
        assertTrue(d.disableSsl)
    }

    @Test
    fun `ConnectionDetails fromUrl rejects invalid, portless, and malformed URLs`() {
        assertFailsWith<PlatformException> { ConnectionDetails.fromUrl("postgres://dbuser:hunter2@pgdatabase:5432_yo/database") }
        assertFailsWith<PlatformException> { ConnectionDetails.fromUrl("://not-a-url") }
        assertFailsWith<PlatformException> { ConnectionDetails.fromUrl("postgres://dbuser:hunter2@pgdatabase/database") }
    }

    @Test
    fun `ConnectionDetails fromUrl leaves DisableSSL false without sslmode disable`() {
        assertFalse(ConnectionDetails.fromUrl("postgres://dbuser:hunter2@pgdatabase:5432/database").disableSsl)
    }

    @Test
    fun `postgresConnectionString quotes and escapes values so they cannot inject parameters`() {
        val d = ConnectionDetails(username = "admin", password = "p'w s host=evil", database = "mydb", host = "dbhost", port = 5432)
        assertEquals(
            "user='admin' password='p\\'w s host=evil' database='mydb' host='dbhost' port=5432 sslmode=prefer",
            d.postgresConnectionString(),
        )
    }

    @Test
    fun `postgresConnectionString honors DisableSSL`() {
        val d = ConnectionDetails(username = "a", password = "b", database = "c", host = "h", port = 1, disableSsl = true)
        assertTrue(d.postgresConnectionString().contains("sslmode=disable"))
    }

    @Test
    fun `uri renders and honors DisableSSL`() {
        val d = ConnectionDetails(username = "admin", password = "secret", database = "mydb", host = "dbhost", port = 5432)
        assertEquals("postgres://admin:secret@dbhost:5432/mydb?sslmode=prefer", d.uri())
        assertEquals(
            "postgres://admin:secret@dbhost:5432/mydb?sslmode=disable",
            d.copy(disableSsl = true).uri(),
        )
    }

    @Test
    fun `uri percent-encodes credentials with special characters`() {
        val d = ConnectionDetails(username = "ad@min", password = "p@ss/word?x", database = "mydb", host = "dbhost", port = 5432)
        val parsed = URI(d.uri())
        val userInfo = parsed.userInfo
        assertEquals("p@ss/word?x", userInfo.substring(userInfo.indexOf(':') + 1))
    }

    @Test
    fun `mysqlDsn renders the standard form with parseTime`() {
        val d = ConnectionDetails(username = "admin", password = "secret", database = "mydb", host = "dbhost", port = 3306)
        assertEquals("admin:secret@tcp(dbhost:3306)/mydb?parseTime=true", d.mysqlDsn())
    }

    @Test
    fun `sqliteDsn returns the file path`() {
        assertEquals("/tmp/test.db", ConnectionDetails(database = "/tmp/test.db").sqliteDsn())
        assertEquals(":memory:", ConnectionDetails(database = ":memory:").sqliteDsn())
    }

    @Test
    fun `connection strings are provider-aware`() {
        val pg = ConnectionDetails("user", "pass", "db", "localhost", 5432)
        assertEquals(
            "user='user' password='pass' database='db' host='localhost' port=5432 sslmode=prefer",
            DatabaseConfig(provider = DatabaseProvider.POSTGRES, readConnection = pg).readConnectionString(),
        )

        val my = ConnectionDetails("user", "pass", "db", "localhost", 3306)
        assertEquals(
            "user:pass@tcp(localhost:3306)/db?parseTime=true",
            DatabaseConfig(provider = DatabaseProvider.MYSQL, readConnection = my).readConnectionString(),
        )

        assertEquals(
            "/tmp/test.db",
            DatabaseConfig(provider = DatabaseProvider.SQLITE, readConnection = ConnectionDetails(database = "/tmp/test.db"))
                .readConnectionString(),
        )

        assertEquals(
            ":memory:",
            DatabaseConfig(provider = DatabaseProvider.SQLITE, writeConnection = ConnectionDetails(database = ":memory:"))
                .writeConnectionString(),
        )
    }

    @Test
    fun `driverName maps each provider`() {
        assertEquals("pgx", DatabaseConfig(provider = DatabaseProvider.POSTGRES).driverName())
        assertEquals("mysql", DatabaseConfig(provider = DatabaseProvider.MYSQL).driverName())
        assertEquals("sqlite", DatabaseConfig(provider = DatabaseProvider.SQLITE).driverName())
    }
}
