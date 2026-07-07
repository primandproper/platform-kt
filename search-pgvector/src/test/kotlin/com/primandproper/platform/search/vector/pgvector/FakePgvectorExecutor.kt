package com.primandproper.platform.search.vector.pgvector

/** A single recorded statement: the SQL text and the bound parameters. */
data class RecordedStatement(
    val sql: String,
    val params: List<Any?>,
)

/**
 * A fake [PgvectorExecutor] that records every statement it received and returns canned rows for
 * queries, standing in for a live Postgres so [PgvectorIndexManager]'s SQL-building and row-mapping
 * logic can be unit-tested — the analog of `:cache-redis`'s `FakeRedisClient`. pgvector's SQL is
 * Postgres-specific and does not run on H2, so a recording fake (not a real database) is the faithful
 * way to assert the exact statements and parameters the manager builds.
 */
class FakePgvectorExecutor(
    var failOn: String? = null,
) : PgvectorExecutor {
    /** Every non-transaction execute, in order. */
    val executed: MutableList<RecordedStatement> = mutableListOf()

    /** Every execute issued inside a transaction, in order. */
    val transactionStatements: MutableList<RecordedStatement> = mutableListOf()

    /** Every query, in order. */
    val queried: MutableList<RecordedStatement> = mutableListOf()

    var transactionCount: Int = 0

    /** Rows returned from [query]; set per test. */
    var queryRows: List<PgvectorRow> = emptyList()

    override suspend fun transaction(block: suspend (PgvectorTransaction) -> Unit) {
        if (failOn == "transaction") throw RuntimeException("injected transaction failure")
        transactionCount++
        block(
            object : PgvectorTransaction {
                override suspend fun execute(
                    sql: String,
                    params: List<Any?>,
                ) {
                    if (failOn == "tx.execute") throw RuntimeException("injected tx.execute failure")
                    transactionStatements += RecordedStatement(sql, params)
                }
            },
        )
    }

    override suspend fun execute(
        sql: String,
        params: List<Any?>,
    ) {
        if (failOn == "execute") throw RuntimeException("injected execute failure")
        executed += RecordedStatement(sql, params)
    }

    override suspend fun query(
        sql: String,
        params: List<Any?>,
    ): List<PgvectorRow> {
        if (failOn == "query") throw RuntimeException("injected query failure")
        queried += RecordedStatement(sql, params)
        return queryRows
    }
}

/** A canned [PgvectorRow] for driving [PgvectorIndexManager.query] mapping in tests. */
class FakeRow(
    private val values: Map<String, Any?>,
) : PgvectorRow {
    override fun string(column: String): String = stringOrNull(column) ?: error("column \"$column\" is null")

    override fun stringOrNull(column: String): String? = values[column]?.toString()

    override fun double(column: String): Double =
        when (val v = values[column]) {
            is Number -> v.toDouble()
            else -> error("column \"$column\" is not a number")
        }
}
