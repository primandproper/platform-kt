package com.primandproper.platform.database.postgres.tableaccess

import com.primandproper.platform.errors.PlatformException
import kotlinx.coroutines.test.runTest
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of the logic in platform-go's `database/postgres/tableaccess/access_manager_test.go` that runs
 * without a live database: privilege validation, identifier/literal quoting, and DDL construction. The
 * Go tests exercise the executing paths against a testcontainers Postgres; those are out of scope for a
 * no-live-infra unit test, so the SQL builders are asserted directly instead.
 */
class TableAccessManagerTest {
    @Test
    fun `Privilege isValid accepts known privileges and rejects others`() {
        assertTrue(Privilege.isValid("SELECT"))
        assertTrue(Privilege.isValid("CONNECT"))
        assertFalse(Privilege.isValid("DROP"))
        assertFalse(Privilege.isValid("select"))
    }

    @Test
    fun `quoteIdent double-quotes and escapes embedded quotes`() {
        assertEquals("\"table\"", quoteIdent("table"))
        assertEquals("\"a\"\"b\"", quoteIdent("a\"b"))
    }

    @Test
    fun `quoteLiteral single-quotes and escapes embedded quotes`() {
        assertEquals("'secret'", quoteLiteral("secret"))
        assertEquals("'O''Brien'", quoteLiteral("O'Brien"))
    }

    @Test
    fun `SQL builders quote every identifier and literal`() {
        assertEquals("CREATE USER \"admin\" WITH PASSWORD 'hunter2'", createUserSql("admin", "hunter2"))
        assertEquals("DROP USER IF EXISTS \"admin\"", deleteUserSql("admin"))
        assertEquals("CREATE DATABASE \"app\" OWNER \"admin\"", createDatabaseSql("app", "admin"))
        assertEquals("DROP DATABASE IF EXISTS \"app\"", deleteDatabaseSql("app"))
        assertEquals("GRANT SELECT ON TABLE \"public\".\"users\" TO \"admin\"", grantSql("admin", "public", "users", "SELECT"))
    }

    @Test
    fun `createUserSql keeps an injection payload inside the quoted literal`() {
        // The password's quote is doubled, so it cannot break out and add statements.
        assertEquals(
            "CREATE USER \"admin\" WITH PASSWORD 'p''; DROP TABLE users;--'",
            createUserSql("admin", "p'; DROP TABLE users;--"),
        )
    }

    @Test
    fun `grantUserAccessToTable rejects an invalid privilege before touching the database`() =
        runTest {
            val manager = TableAccessManager(throwingDataSource())
            assertFailsWith<PlatformException> {
                manager.grantUserAccessToTable("admin", "public", "users", "DROP")
            }
        }

    /** A [DataSource] whose every method throws — proves the privilege check short-circuits before any query. */
    private fun throwingDataSource(): DataSource =
        java.lang.reflect.Proxy.newProxyInstance(
            DataSource::class.java.classLoader,
            arrayOf(DataSource::class.java),
        ) { _, method, _ -> throw UnsupportedOperationException("stub DataSource: ${method.name}") } as DataSource
}
