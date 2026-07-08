package com.primandproper.platform.database.mock

import com.primandproper.platform.database.DatabaseClient
import com.primandproper.platform.database.ResultIterator
import com.primandproper.platform.database.Scanner
import com.primandproper.platform.database.SqlQueryExecutor
import com.primandproper.platform.database.SqlQueryExecutorAndTransactionManager
import com.primandproper.platform.database.SqlResult
import java.sql.PreparedStatement
import java.time.Instant
import javax.sql.DataSource

/**
 * Configurable test doubles for the database contracts, mirroring platform-go's moq-generated
 * `mockdatabase` package (`ClientMock`, `ResultIteratorMock`, `SQLQueryExecutorMock`). Each method
 * delegates to a settable `...Func`; calling a method whose `Func` is `null` throws
 * [IllegalStateException] — the analog of moq's generated panic — and every call is recorded in the
 * matching `...Calls` list, standing in for moq's `XCalls()` accessors. Same contract as `:cache-api`'s
 * `CacheMock`.
 */
public class ClientMock(
    public var writeDataSourceFunc: (() -> DataSource)? = null,
    public var readDataSourceFunc: (() -> DataSource)? = null,
    public var currentTimeFunc: (() -> Instant)? = null,
    public var rollbackTransactionFunc: (suspend (SqlQueryExecutorAndTransactionManager) -> Unit)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : DatabaseClient {
    public val writeDataSourceCalls: MutableList<Unit> = mutableListOf()
    public val readDataSourceCalls: MutableList<Unit> = mutableListOf()
    public val currentTimeCalls: MutableList<Unit> = mutableListOf()
    public val rollbackTransactionCalls: MutableList<SqlQueryExecutorAndTransactionManager> = mutableListOf()
    public val closeCalls: MutableList<Unit> = mutableListOf()

    override val writeDataSource: DataSource
        get() {
            writeDataSourceCalls += Unit
            return requireFunc(writeDataSourceFunc, "writeDataSourceFunc").invoke()
        }

    override val readDataSource: DataSource
        get() {
            readDataSourceCalls += Unit
            return requireFunc(readDataSourceFunc, "readDataSourceFunc").invoke()
        }

    override fun currentTime(): Instant {
        currentTimeCalls += Unit
        return requireFunc(currentTimeFunc, "currentTimeFunc").invoke()
    }

    override suspend fun rollbackTransaction(tx: SqlQueryExecutorAndTransactionManager) {
        rollbackTransactionCalls += tx
        requireFunc(rollbackTransactionFunc, "rollbackTransactionFunc").invoke(tx)
    }

    override fun close() {
        closeCalls += Unit
        requireFunc(closeFunc, "closeFunc").invoke()
    }
}

/** A configurable [ResultIterator] double, mirroring moq's `ResultIteratorMock`. */
public class ResultIteratorMock(
    public var nextFunc: (() -> Boolean)? = null,
    public var errFunc: (() -> Throwable?)? = null,
    public var scanFunc: ((List<Any?>) -> Unit)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : ResultIterator {
    public val nextCalls: MutableList<Unit> = mutableListOf()
    public val errCalls: MutableList<Unit> = mutableListOf()
    public val scanCalls: MutableList<List<Any?>> = mutableListOf()
    public val closeCalls: MutableList<Unit> = mutableListOf()

    override fun next(): Boolean {
        nextCalls += Unit
        return requireFunc(nextFunc, "nextFunc").invoke()
    }

    override fun err(): Throwable? {
        errCalls += Unit
        return requireFunc(errFunc, "errFunc").invoke()
    }

    override fun scan(vararg dest: Any?) {
        val args = dest.toList()
        scanCalls += args
        requireFunc(scanFunc, "scanFunc").invoke(args)
    }

    override fun close() {
        closeCalls += Unit
        requireFunc(closeFunc, "closeFunc").invoke()
    }
}

/** A configurable [SqlQueryExecutor] double, mirroring moq's `SQLQueryExecutorMock`. */
public class SqlQueryExecutorMock(
    public var execFunc: (suspend (String, List<Any?>) -> SqlResult)? = null,
    public var prepareFunc: (suspend (String) -> PreparedStatement)? = null,
    public var queryFunc: (suspend (String, List<Any?>) -> ResultIterator)? = null,
    public var queryRowFunc: (suspend (String, List<Any?>) -> Scanner)? = null,
) : SqlQueryExecutor {
    public val execCalls: MutableList<Pair<String, List<Any?>>> = mutableListOf()
    public val prepareCalls: MutableList<String> = mutableListOf()
    public val queryCalls: MutableList<Pair<String, List<Any?>>> = mutableListOf()
    public val queryRowCalls: MutableList<Pair<String, List<Any?>>> = mutableListOf()

    override suspend fun exec(
        query: String,
        vararg args: Any?,
    ): SqlResult {
        val a = args.toList()
        execCalls += query to a
        return requireFunc(execFunc, "execFunc").invoke(query, a)
    }

    override suspend fun prepare(query: String): PreparedStatement {
        prepareCalls += query
        return requireFunc(prepareFunc, "prepareFunc").invoke(query)
    }

    override suspend fun query(
        query: String,
        vararg args: Any?,
    ): ResultIterator {
        val a = args.toList()
        queryCalls += query to a
        return requireFunc(queryFunc, "queryFunc").invoke(query, a)
    }

    override suspend fun queryRow(
        query: String,
        vararg args: Any?,
    ): Scanner {
        val a = args.toList()
        queryRowCalls += query to a
        return requireFunc(queryRowFunc, "queryRowFunc").invoke(query, a)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
