package com.primandproper.platform.database.mock

import com.primandproper.platform.database.SqlResult
import kotlinx.coroutines.test.runTest
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Exercises the configurable doubles, mirroring the contract of platform-go's moq-generated mocks. */
class DatabaseMockTest {
    private val dataSource: DataSource = throwingDataSource()

    private val result =
        object : SqlResult {
            override fun lastInsertId(): Long = 7

            override fun rowsAffected(): Long = 1
        }

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

            assertEquals(1, mock.writeDataSourceCalls.size)
            assertEquals(1, mock.readDataSourceCalls.size)
            assertEquals(1, mock.currentTimeCalls.size)
            assertEquals(1, mock.closeCalls.size)
        }

    @Test
    fun `ClientMock throws when a func is unset`() {
        assertFailsWith<IllegalStateException> { ClientMock().currentTime() }
    }

    @Test
    fun `ResultIteratorMock records scan and iteration calls`() {
        val mock =
            ResultIteratorMock(
                nextFunc = { false },
                errFunc = { null },
                scanFunc = { },
                closeFunc = { },
            )

        assertFalse(mock.next())
        assertNull(mock.err())
        mock.scan("a", 1)
        mock.close()

        assertEquals(listOf(listOf<Any?>("a", 1)), mock.scanCalls)
        assertEquals(1, mock.nextCalls.size)
        assertEquals(1, mock.closeCalls.size)
    }

    @Test
    fun `SqlQueryExecutorMock records exec and query calls`() =
        runTest {
            val mock =
                SqlQueryExecutorMock(
                    execFunc = { _, _ -> result },
                    queryFunc = { _, _ -> ResultIteratorMock(nextFunc = { false }) },
                )

            assertEquals(1L, mock.exec("INSERT INTO t VALUES (?)", 1).rowsAffected())
            mock.query("SELECT 1")

            assertEquals(listOf("INSERT INTO t VALUES (?)" to listOf<Any?>(1)), mock.execCalls)
            assertEquals(listOf("SELECT 1" to emptyList<Any?>()), mock.queryCalls)
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
