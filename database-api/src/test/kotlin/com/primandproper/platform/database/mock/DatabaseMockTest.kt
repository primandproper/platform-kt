package com.primandproper.platform.database.mock

import com.primandproper.platform.database.MapRow
import com.primandproper.platform.database.SqlResult
import com.primandproper.platform.database.query
import com.primandproper.platform.database.queryOne
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Exercises the configurable doubles for the redesigned, Flow/Row-based query surface. */
class DatabaseMockTest {
    private val dataSource: DataSource = throwingDataSource()

    private val result = SqlResult(lastInsertId = 7, rowsAffected = 1)

    @Test
    fun `ClientMock invokes configured funcs and records calls`() =
        runTest {
            val now = Instant.parse("2026-07-07T12:00:00Z")
            val mock =
                ClientMock(
                    writeDataSourceFunc = { dataSource },
                    readDataSourceFunc = { dataSource },
                    currentTimeFunc = { now },
                    rollbackTransactionFunc = { },
                    closeFunc = { },
                )

            assertSame(dataSource, mock.writeDataSource)
            assertSame(dataSource, mock.readDataSource)
            assertEquals(now, mock.currentTime())
            mock.close()

            assertEquals(1, mock.writeDataSourceCalls)
            assertEquals(1, mock.readDataSourceCalls)
            assertEquals(1, mock.currentTimeCalls)
            assertEquals(1, mock.closeCalls)
        }

    @Test
    fun `ClientMock throws when a func is unset`() {
        assertFailsWith<IllegalStateException> { ClientMock().currentTime() }
    }

    @Test
    fun `MapRow reads typed columns case-insensitively`() {
        val ts = Instant.parse("2026-07-07T12:00:00Z")
        val row = MapRow(mapOf("Id" to 42L, "Name" to "ada", "ACTIVE" to true, "created" to java.sql.Timestamp.from(ts)))

        assertEquals(42L, row.long("id"))
        assertEquals(42, row.int("ID"))
        assertEquals("ada", row.string("name"))
        assertEquals(true, row.boolean("active"))
        assertEquals(ts, row.instant("created"))
        assertEquals(null, row.stringOrNull("missing"))
    }

    @Test
    fun `MapRow non-null accessor throws on a NULL column`() {
        val row = MapRow(mapOf("name" to null))
        assertFailsWith<IllegalStateException> { row.string("name") }
    }

    @Test
    fun `SqlQueryExecutorMock records exec and streams query rows`() =
        runTest {
            val mock =
                SqlQueryExecutorMock(
                    execFunc = { _, _ -> result },
                    queryFunc = { _, _ -> flowOf(MapRow(mapOf("id" to 1L)), MapRow(mapOf("id" to 2L))) },
                    queryOneFunc = { _, _ -> MapRow(mapOf("id" to 9L)) },
                )

            assertEquals(1L, mock.exec("INSERT INTO t VALUES (?)", 1).rowsAffected)

            val ids = mock.query("SELECT id FROM t") { it.long("id") }.toList()
            assertEquals(listOf(1L, 2L), ids)

            assertEquals(9L, mock.queryOne("SELECT id FROM t LIMIT 1") { it.long("id") })

            assertEquals(listOf("INSERT INTO t VALUES (?)" to listOf<Any?>(1)), mock.execCalls)
            assertEquals(listOf("SELECT id FROM t" to emptyList<Any?>()), mock.queryCalls)
        }

    @Test
    fun `SqlQueryExecutorMock query defaults to an empty flow`() =
        runTest {
            assertEquals(emptyList(), SqlQueryExecutorMock().query("SELECT 1").toList())
        }

    @Test
    fun `SqlQueryExecutorMock withPrepared runs the block against a handle`() =
        runTest {
            val handle = PreparedHandleMock(executeUpdateFunc = { SqlResult(rowsAffected = 3) })
            val mock = SqlQueryExecutorMock(preparedHandleFunc = { handle })

            val affected =
                mock.withPrepared("UPDATE t SET x = ? WHERE id = ?") { h ->
                    h.bindAll("v", 5L).executeUpdate().rowsAffected
                }

            assertEquals(3L, affected)
            assertEquals(listOf("UPDATE t SET x = ? WHERE id = ?"), mock.withPreparedCalls)
            assertEquals(listOf(listOf<Any?>("v", 5L)), handle.bindAllCalls)
            assertEquals(1, handle.executeUpdateCalls)
        }

    @Test
    fun `SqlQueryExecutorMock throws when a func is unset`() =
        runTest {
            assertFailsWith<IllegalStateException> { SqlQueryExecutorMock().exec("SELECT 1") }
        }
}

/** A [DataSource] whose every method throws; the mock only needs an identity, never a real connection. */
private fun throwingDataSource(): DataSource =
    java.lang.reflect.Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
    ) { proxy, method, _ ->
        when (method.name) {
            "equals" -> false
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "stubDataSource"
            else -> throw UnsupportedOperationException("stub DataSource: ${method.name}")
        }
    } as DataSource
