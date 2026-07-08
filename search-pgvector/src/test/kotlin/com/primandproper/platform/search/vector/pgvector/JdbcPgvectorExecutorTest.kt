package com.primandproper.platform.search.vector.pgvector

import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves [JdbcPgvectorExecutor]'s provider-agnostic transaction/exec/query wiring end-to-end against
 * in-memory H2. pgvector's `vector`-typed SQL only runs on real Postgres, so these tests exercise the
 * adapter with plain SQL — the manager's SQL-string building is covered separately against the fake
 * executor in [PgvectorIndexManagerTest].
 */
class JdbcPgvectorExecutorTest {
    // JVM-global so each unique-DB name is never reused. JUnit5 makes a fresh test instance per
    // method, so an instance-level counter would reset to the same name every test and, with
    // DB_CLOSE_DELAY=-1 keeping the H2 mem DB alive, collide on "table already exists".
    private companion object {
        val dbCounter = AtomicInteger(0)
    }

    private fun freshDataSource(): DataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:pgvec${dbCounter.incrementAndGet()};DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }

    @Test
    fun `execute then query round-trips values`() =
        runTest {
            val exec = JdbcPgvectorExecutor(freshDataSource())
            exec.execute("CREATE TABLE items (id VARCHAR PRIMARY KEY, dist DOUBLE)", emptyList())
            exec.execute("INSERT INTO items (id, dist) VALUES (?, ?)", listOf("a", 0.5))

            val rows = exec.query("SELECT id, dist AS distance FROM items ORDER BY id", emptyList())

            assertEquals(1, rows.size)
            assertEquals("a", rows.single().string("id"))
            assertEquals(0.5, rows.single().double("distance"))
        }

    @Test
    fun `transaction commits every statement`() =
        runTest {
            val ds = freshDataSource()
            val exec = JdbcPgvectorExecutor(ds)
            exec.execute("CREATE TABLE items (id VARCHAR PRIMARY KEY)", emptyList())

            exec.transaction { tx ->
                tx.execute("INSERT INTO items (id) VALUES (?)", listOf("a"))
                tx.execute("INSERT INTO items (id) VALUES (?)", listOf("b"))
            }

            val rows = exec.query("SELECT id FROM items", emptyList())
            assertEquals(2, rows.size)
        }

    @Test
    fun `transaction rolls back on failure`() =
        runTest {
            val exec = JdbcPgvectorExecutor(freshDataSource())
            exec.execute("CREATE TABLE items (id VARCHAR PRIMARY KEY)", emptyList())

            assertFailsWith<RuntimeException> {
                exec.transaction { tx ->
                    tx.execute("INSERT INTO items (id) VALUES (?)", listOf("a"))
                    throw RuntimeException("boom")
                }
            }

            val rows = exec.query("SELECT id FROM items", emptyList())
            assertEquals(0, rows.size)
        }

    @Test
    fun `stringOrNull returns null for a null column`() =
        runTest {
            val exec = JdbcPgvectorExecutor(freshDataSource())
            exec.execute("CREATE TABLE items (id VARCHAR PRIMARY KEY, meta VARCHAR)", emptyList())
            exec.execute("INSERT INTO items (id, meta) VALUES (?, ?)", listOf("a", null))

            val row = exec.query("SELECT id, meta FROM items", emptyList()).single()
            assertEquals("a", row.string("id"))
            assertEquals(null, row.stringOrNull("meta"))
        }
}
