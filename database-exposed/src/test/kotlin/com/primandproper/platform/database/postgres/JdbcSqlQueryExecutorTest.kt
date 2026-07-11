package com.primandproper.platform.database.postgres

import com.primandproper.platform.database.executeQuery
import com.primandproper.platform.database.query
import com.primandproper.platform.database.queryOne
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the redesigned Flow/Row query surface end-to-end against an in-memory H2 database. */
class JdbcSqlQueryExecutorTest {
    private companion object {
        // Static so each test method (JUnit makes a fresh instance per method) gets a unique
        // mem-DB name; otherwise the `DB_CLOSE_DELAY=-1` database survives the JVM and the next
        // test's `CREATE TABLE users` collides with the surviving table.
        private val dbCounter = AtomicInteger(0)
    }

    private fun h2Table(): DataSource {
        val ds =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:exec-test-${dbCounter.incrementAndGet()};DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        ds.connection.use { conn ->
            conn.createStatement().use {
                it.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), active BOOLEAN)")
            }
        }
        return ds
    }

    @Test
    fun `exec inserts and reports rows affected and a generated key`() =
        runTest {
            val exec = JdbcSqlQueryExecutor(h2Table())

            val result = exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "ada", true)

            assertEquals(1L, result.rowsAffected)
            assertEquals(1L, result.lastInsertId)
        }

    @Test
    fun `query streams typed rows through a mapper`() =
        runTest {
            val exec = JdbcSqlQueryExecutor(h2Table())
            exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "ada", true)
            exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "grace", false)

            val names = exec.query("SELECT name FROM users ORDER BY id") { it.string("name") }.toList()
            assertEquals(listOf("ada", "grace"), names)

            val actives = exec.query("SELECT active FROM users ORDER BY id").toList().map { it.boolean("active") }
            assertEquals(listOf(true, false), actives)
        }

    @Test
    fun `queryOne returns the single row or null`() =
        runTest {
            val exec = JdbcSqlQueryExecutor(h2Table())
            exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "ada", true)

            val id = exec.queryOne("SELECT id, name FROM users WHERE name = ?", "ada") { it.int("id") }
            assertEquals(1, id)

            assertNull(exec.queryOne("SELECT id FROM users WHERE name = ?", "nobody"))
        }

    @Test
    fun `withPrepared binds, updates, and re-queries within the bracket`() =
        runTest {
            val exec = JdbcSqlQueryExecutor(h2Table())
            exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "ada", true)
            exec.exec("INSERT INTO users (name, active) VALUES (?, ?)", "grace", true)

            val renamed =
                exec.withPrepared("UPDATE users SET name = ? WHERE name = ?") { handle ->
                    handle.bindAll("Ada Lovelace", "ada").executeUpdate().rowsAffected
                }
            assertEquals(1L, renamed)

            val names =
                exec.withPrepared("SELECT name FROM users ORDER BY id") { handle ->
                    handle.executeQuery { it.string("name") }.toList()
                }
            assertTrue(names.contains("Ada Lovelace"))
        }
}
