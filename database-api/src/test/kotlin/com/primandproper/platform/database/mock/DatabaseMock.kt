package com.primandproper.platform.database.mock

import com.primandproper.platform.database.DatabaseClient
import com.primandproper.platform.database.PreparedHandle
import com.primandproper.platform.database.Row
import com.primandproper.platform.database.SqlQueryExecutor
import com.primandproper.platform.database.SqlQueryExecutorAndTransactionManager
import com.primandproper.platform.database.SqlResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import javax.sql.DataSource

/**
 * Configurable test doubles for the database contracts, mirroring platform-go's moq-generated
 * `mockdatabase` package (`ClientMock`, `SQLQueryExecutorMock`), updated for the redesigned Flow/Row
 * query surface (see P3-3). Each method
 * delegates to a settable `...Func`; calling a method whose `Func` is `null` throws
 * [IllegalStateException] — the analog of moq's generated panic — and every call is recorded in the
 * matching `...Calls` list, standing in for moq's `XCalls()` accessors. Same contract as `:cache-api`'s
 * `CacheMock`.
 *
 * Recording is guarded by a per-mock lock and the `...Calls` accessors hand back an immutable snapshot,
 * so a recorder on one thread can't throw [ConcurrentModificationException] against a reader on another.
 */
public class ClientMock(
    public var writeDataSourceFunc: (() -> DataSource)? = null,
    public var readDataSourceFunc: (() -> DataSource)? = null,
    public var currentTimeFunc: (() -> Instant)? = null,
    public var rollbackTransactionFunc: (suspend (SqlQueryExecutorAndTransactionManager) -> Unit)? = null,
    public var closeFunc: (() -> Unit)? = null,
) : DatabaseClient {
    private val lock = Any()
    private var _writeDataSourceCalls = 0
    private var _readDataSourceCalls = 0
    private var _currentTimeCalls = 0
    private val _rollbackTransactionCalls = mutableListOf<SqlQueryExecutorAndTransactionManager>()
    private var _closeCalls = 0

    public val writeDataSourceCalls: Int get() = synchronized(lock) { _writeDataSourceCalls }
    public val readDataSourceCalls: Int get() = synchronized(lock) { _readDataSourceCalls }
    public val currentTimeCalls: Int get() = synchronized(lock) { _currentTimeCalls }
    public val rollbackTransactionCalls: List<SqlQueryExecutorAndTransactionManager>
        get() = synchronized(lock) { _rollbackTransactionCalls.toList() }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override val writeDataSource: DataSource
        get() {
            synchronized(lock) { _writeDataSourceCalls++ }
            return requireFunc(writeDataSourceFunc, "writeDataSourceFunc").invoke()
        }

    override val readDataSource: DataSource
        get() {
            synchronized(lock) { _readDataSourceCalls++ }
            return requireFunc(readDataSourceFunc, "readDataSourceFunc").invoke()
        }

    override fun currentTime(): Instant {
        synchronized(lock) { _currentTimeCalls++ }
        return requireFunc(currentTimeFunc, "currentTimeFunc").invoke()
    }

    override suspend fun rollbackTransaction(tx: SqlQueryExecutorAndTransactionManager) {
        synchronized(lock) { _rollbackTransactionCalls += tx }
        requireFunc(rollbackTransactionFunc, "rollbackTransactionFunc").invoke(tx)
    }

    override fun close() {
        synchronized(lock) { _closeCalls++ }
        requireFunc(closeFunc, "closeFunc").invoke()
    }
}

/**
 * A configurable [SqlQueryExecutor] double for the redesigned query surface. Each method delegates to
 * a settable `...Func` (an unset `Func` throws when the method is called) and records its call; unset
 * `queryFunc` defaults to an [emptyFlow] so a read that is never stubbed simply yields no rows.
 *
 * Recording is guarded by a per-mock lock and the `...Calls` accessors hand back an immutable snapshot.
 */
public class SqlQueryExecutorMock(
    public var execFunc: (suspend (String, List<Any?>) -> SqlResult)? = null,
    public var queryFunc: ((String, List<Any?>) -> Flow<Row>)? = null,
    public var queryOneFunc: (suspend (String, List<Any?>) -> Row?)? = null,
    public var preparedHandleFunc: ((String) -> PreparedHandle)? = null,
) : SqlQueryExecutor {
    private val lock = Any()
    private val _execCalls = mutableListOf<Pair<String, List<Any?>>>()
    private val _queryCalls = mutableListOf<Pair<String, List<Any?>>>()
    private val _queryOneCalls = mutableListOf<Pair<String, List<Any?>>>()
    private val _withPreparedCalls = mutableListOf<String>()

    public val execCalls: List<Pair<String, List<Any?>>> get() = synchronized(lock) { _execCalls.toList() }
    public val queryCalls: List<Pair<String, List<Any?>>> get() = synchronized(lock) { _queryCalls.toList() }
    public val queryOneCalls: List<Pair<String, List<Any?>>> get() = synchronized(lock) { _queryOneCalls.toList() }
    public val withPreparedCalls: List<String> get() = synchronized(lock) { _withPreparedCalls.toList() }

    override suspend fun exec(
        query: String,
        vararg args: Any?,
    ): SqlResult {
        val a = args.toList()
        synchronized(lock) { _execCalls += query to a }
        return requireFunc(execFunc, "execFunc").invoke(query, a)
    }

    override fun query(
        query: String,
        vararg args: Any?,
    ): Flow<Row> {
        val a = args.toList()
        synchronized(lock) { _queryCalls += query to a }
        return queryFunc?.invoke(query, a) ?: emptyFlow()
    }

    override suspend fun queryOne(
        query: String,
        vararg args: Any?,
    ): Row? {
        val a = args.toList()
        synchronized(lock) { _queryOneCalls += query to a }
        return requireFunc(queryOneFunc, "queryOneFunc").invoke(query, a)
    }

    override suspend fun <T> withPrepared(
        sql: String,
        block: suspend (PreparedHandle) -> T,
    ): T {
        synchronized(lock) { _withPreparedCalls += sql }
        return block(requireFunc(preparedHandleFunc, "preparedHandleFunc").invoke(sql))
    }
}

/**
 * A configurable [PreparedHandle] double. Binds are recorded (index → value, and each [bindAll] batch);
 * [executeUpdate]/[executeQuery] delegate to settable `...Func`s (an unset `executeQueryFunc` yields no
 * rows).
 *
 * Recording is guarded by a per-mock lock and the `...Calls` accessors hand back an immutable snapshot.
 */
public class PreparedHandleMock(
    public var executeUpdateFunc: (suspend () -> SqlResult)? = null,
    public var executeQueryFunc: (() -> Flow<Row>)? = null,
) : PreparedHandle {
    private val lock = Any()
    private val _bindCalls = mutableListOf<Pair<Int, Any?>>()
    private val _bindAllCalls = mutableListOf<List<Any?>>()
    private var _executeUpdateCalls = 0
    private var _executeQueryCalls = 0

    public val bindCalls: List<Pair<Int, Any?>> get() = synchronized(lock) { _bindCalls.toList() }
    public val bindAllCalls: List<List<Any?>> get() = synchronized(lock) { _bindAllCalls.toList() }
    public val executeUpdateCalls: Int get() = synchronized(lock) { _executeUpdateCalls }
    public val executeQueryCalls: Int get() = synchronized(lock) { _executeQueryCalls }

    override fun bind(
        index: Int,
        value: Any?,
    ): PreparedHandle {
        synchronized(lock) { _bindCalls += index to value }
        return this
    }

    override fun bindAll(vararg values: Any?): PreparedHandle {
        synchronized(lock) { _bindAllCalls += values.toList() }
        return this
    }

    override suspend fun executeUpdate(): SqlResult {
        synchronized(lock) { _executeUpdateCalls++ }
        return requireFunc(executeUpdateFunc, "executeUpdateFunc").invoke()
    }

    override fun executeQuery(): Flow<Row> {
        synchronized(lock) { _executeQueryCalls++ }
        return executeQueryFunc?.invoke() ?: emptyFlow()
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
