package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.errors.InvalidIDProvidedException
import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.wrap
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.search.vector.DimensionMismatchException
import com.primandproper.platform.search.vector.DistanceMetric
import com.primandproper.platform.search.vector.EmptyEmbeddingException
import com.primandproper.platform.search.vector.Index
import com.primandproper.platform.search.vector.ProviderFilter
import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.QueryResult
import com.primandproper.platform.search.vector.Vector

/** Thrown when an index or column name did not meet the bare-identifier constraint. Mirrors Go's `ErrInvalidIdentifier`. */
public class InvalidIdentifierException : PlatformException("identifier must match [A-Za-z_][A-Za-z0-9_]*")

/**
 * A pgvector-backed vector [Index]. Port of platform-go's `search/vector/pgvector.indexManager[T]`.
 *
 * SQL is issued through an injected [PgvectorExecutor] — [JdbcPgvectorExecutor] in production, a fake
 * in tests — so the SQL-building and row-mapping logic here is exercised without a live Postgres,
 * mirroring how `:cache-redis` tests `RedisCache` against a fake `RedisClient`. Metadata crosses the
 * boundary as `jsonb` text via the injected [MetadataCodec].
 *
 * Every method opens an [Observer] span recording the index name and batch length / result count,
 * mirroring `i.o11y.Begin(ctx)` / `op.Set(...)`; the `span` scope records and rethrows any thrown SQL
 * error exactly once.
 *
 * Placeholders are JDBC `?` rather than Postgres' `$N` (see [PgvectorExecutor]). Identifiers are both
 * validated against [safeIdentifier] and double-quoted before interpolation, so the only values
 * interpolated into a statement are internally-quoted identifiers; every row value is a bound parameter.
 *
 * TODO(metrics): platform-go records upsert/delete/wipe/query/error counters and a latency histogram
 * through a metrics provider. platform-kt's observability-api has no metrics pillar, so those are a
 * documented seam, the same descope `:cache-redis` makes.
 *
 * TODO(circuitbreaking): Go wraps every operation with a `circuitbreaking.CircuitBreaker`. That module
 * is outside this port's dependency set — inject one here once available downstream.
 *
 * TODO(advisory-lock): Go serializes concurrent schema migrations with a transaction-scoped
 * `pg_advisory_xact_lock`. [ensureTable] issues the same lock; the fake executor treats it as a no-op,
 * and it is harmless on a single migrating process.
 */
public class PgvectorIndexManager<T : Any> internal constructor(
    private val o11y: Observer,
    private val executor: PgvectorExecutor,
    private val codec: MetadataCodec<T>,
    private val indexName: String,
    metadataColumn: String,
    metric: DistanceMetric,
    private val dimension: Int,
) : Index<T> {
    /**
     * @param executor the SQL command surface; [JdbcPgvectorExecutor] in production, a fake in tests.
     * @param codec turns metadata into its stored `jsonb` text and back.
     * @param config the dimension, metric, and metadata column.
     * @param indexName the table name; must be a bare identifier.
     * @param logger optional root logger; defaults to a noop logger.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        executor: PgvectorExecutor,
        codec: MetadataCodec<T>,
        config: PgvectorConfig,
        indexName: String,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(
        Observer("${SERVICE_NAME}_$indexName", logger, tracerProvider),
        executor,
        codec,
        indexName,
        config.metadataColumn,
        config.metric,
        config.dimension,
    )

    private val metadataColumn: String = requireIdentifier(metadataColumn, "metadata column")
    private val quotedIndex: String = quoteIdent(requireIdentifier(indexName, "index name"))
    private val quotedMetadataCol: String = quoteIdent(this.metadataColumn)
    private val distanceOperator: String = operatorFor(metric)
    private val indexOpsClass: String = opClassFor(metric)

    /**
     * Runs the idempotent schema migration (advisory lock + `CREATE EXTENSION` + `CREATE TABLE` +
     * `CREATE INDEX`) in a single transaction, mirroring Go's `ensureTable`. Called by
     * [provideVectorIndex] after construction (a `suspend` context the constructor cannot provide);
     * safe to call repeatedly.
     */
    public suspend fun ensureTable() {
        o11y.span("EnsureTable") {
            set(INDEX_NAME_KEY, indexName)
            executor.transaction { tx ->
                tx.execute("SELECT pg_advisory_xact_lock(?)", listOf(ENSURE_SCHEMA_LOCK_KEY))
                tx.execute("CREATE EXTENSION IF NOT EXISTS vector", emptyList())
                tx.execute(
                    "CREATE TABLE IF NOT EXISTS $quotedIndex (" +
                        "id text PRIMARY KEY, " +
                        "embedding vector($dimension) NOT NULL, " +
                        "$quotedMetadataCol jsonb NOT NULL DEFAULT '{}'::jsonb)",
                    emptyList(),
                )
                tx.execute(
                    "CREATE INDEX IF NOT EXISTS ${quoteIdent(indexName + "_embedding_idx")} " +
                        "ON $quotedIndex USING hnsw (embedding $indexOpsClass)",
                    emptyList(),
                )
            }
        }
    }

    override suspend fun upsert(vararg vectors: Vector<T>) {
        o11y.span("Upsert") {
            set(INDEX_NAME_KEY, indexName)
            set(Keys.LENGTH, vectors.size)
            if (vectors.isEmpty()) {
                return@span
            }

            // Validate dimensions and prepare per-row payloads up front so we don't open a
            // transaction we then have to roll back.
            val rows =
                vectors.map { v ->
                    if (v.id.isEmpty()) throw InvalidIDProvidedException()
                    if (v.embedding.isEmpty()) throw EmptyEmbeddingException()
                    if (v.embedding.size != dimension) {
                        throw wrap(DimensionMismatchException(), "got ${v.embedding.size}, want $dimension")
                    }
                    listOf(v.id, encodeVector(v.embedding), codec.encode(v.metadata))
                }

            val stmt =
                "INSERT INTO $quotedIndex (id, embedding, $quotedMetadataCol) " +
                    "VALUES (?, ?::vector, ?::jsonb) " +
                    "ON CONFLICT (id) DO UPDATE SET embedding = EXCLUDED.embedding, " +
                    "$quotedMetadataCol = EXCLUDED.$quotedMetadataCol"

            // Run the whole batch in one transaction so a mid-batch failure rolls back the rows
            // already written rather than leaving a partial batch committed.
            executor.transaction { tx ->
                for (row in rows) {
                    tx.execute(stmt, row)
                }
            }
        }
    }

    override suspend fun delete(vararg ids: String) {
        o11y.span("Delete") {
            set(INDEX_NAME_KEY, indexName)
            set(Keys.LENGTH, ids.size)
            if (ids.isEmpty()) {
                return@span
            }
            executor.execute(
                "DELETE FROM $quotedIndex WHERE id = ANY(?::text[])",
                listOf(pgTextArray(ids.toList())),
            )
        }
    }

    override suspend fun wipe() {
        o11y.span("Wipe") {
            set(INDEX_NAME_KEY, indexName)
            executor.execute("TRUNCATE TABLE $quotedIndex", emptyList())
        }
    }

    override suspend fun query(request: QueryRequest): List<QueryResult<T>> =
        o11y.span("Query") {
            set(INDEX_NAME_KEY, indexName)

            if (request.embedding.isEmpty()) throw EmptyEmbeddingException()
            if (request.embedding.size != dimension) {
                throw wrap(DimensionMismatchException(), "got ${request.embedding.size}, want $dimension")
            }
            val topK = if (request.topK <= 0) 10 else request.topK
            set(TOP_K_KEY, topK)

            val where = whereClause(request.filter)
            val stmt =
                "SELECT id, $quotedMetadataCol, embedding $distanceOperator ?::vector AS distance " +
                    "FROM $quotedIndex$where ORDER BY distance ASC LIMIT ?"

            val rows = executor.query(stmt, listOf(encodeVector(request.embedding), topK))
            val results =
                rows.map { row ->
                    QueryResult(
                        id = row.string("id"),
                        distance = row.double("distance").toFloat(),
                        metadata = codec.decode(row.stringOrNull(metadataColumn)),
                    )
                }
            set(Keys.LENGTH, results.size)
            results
        }

    /**
     * Builds the optional WHERE clause from an opaque [filter]. `null` yields no clause; a
     * [ProviderFilter.RawFilter] whose trimmed `expression` is non-blank is appended verbatim (a
     * blank expression yields no clause). The filter type is sealed, so there is no
     * unknown-type arm to reject — the exhaustive `when` covers every variant.
     *
     * SECURITY: a raw filter is concatenated verbatim into the WHERE clause — it is raw,
     * unparameterized SQL. It is a trusted, caller-supplied fragment, NEVER end-user input; callers
     * MUST sanitize anything they interpolate (including tenant scoping). This mirrors Go's documented
     * filter contract; a parameterized builder is intentionally not used because the shared
     * [QueryRequest] carries no args slice.
     */
    private fun whereClause(filter: ProviderFilter?): String =
        when (filter) {
            null -> ""
            is ProviderFilter.RawFilter -> {
                val trimmed = filter.expression.trim()
                if (trimmed.isEmpty()) "" else " WHERE $trimmed"
            }
        }

    private fun requireIdentifier(
        value: String,
        label: String,
    ): String {
        if (!SAFE_IDENTIFIER.matches(value)) {
            throw wrap(InvalidIdentifierException(), "$label $value")
        }
        return value
    }

    private companion object {
        const val SERVICE_NAME = "pgvector_index"
        const val INDEX_NAME_KEY = "search.index"
        const val TOP_K_KEY = "search.top_k"

        // Arbitrary-but-stable int for the transaction-scoped schema advisory lock ("pgvector" ASCII),
        // matching Go's ensureSchemaLockKey.
        const val ENSURE_SCHEMA_LOCK_KEY: Long = 0x7067766563746f72L

        val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        fun operatorFor(metric: DistanceMetric): String =
            when (metric) {
                DistanceMetric.COSINE -> "<=>"
                DistanceMetric.DOT_PRODUCT -> "<#>"
                DistanceMetric.EUCLIDEAN -> "<->"
            }

        fun opClassFor(metric: DistanceMetric): String =
            when (metric) {
                DistanceMetric.COSINE -> "vector_cosine_ops"
                DistanceMetric.DOT_PRODUCT -> "vector_ip_ops"
                DistanceMetric.EUCLIDEAN -> "vector_l2_ops"
            }
    }
}

/**
 * Formats a [FloatArray] as a pgvector text literal: `[1.5,2.5,3.5]`. Port of Go's `encodeVector`.
 * Public so tests (and callers building raw filters) can assert the exact wire form.
 */
public fun encodeVector(embedding: FloatArray): String =
    embedding.joinToString(separator = ",", prefix = "[", postfix = "]") { formatFloat(it) }

/**
 * Formats a `[]string` of ids as a Postgres `text[]` literal: `{a,b,"c with comma"}`. Port of Go's
 * `pgTextArray`; use only for `ANY(?)` id lookups where ids are caller-supplied strings.
 */
public fun pgTextArray(ids: List<String>): String =
    ids.joinToString(separator = ",", prefix = "{", postfix = "}") { id ->
        "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

/**
 * Double-quotes a Postgres identifier, doubling any embedded double-quotes per the SQL spec. Port of
 * Go's `quoteIdent`.
 */
public fun quoteIdent(id: String): String = "\"" + id.replace("\"", "\"\"") + "\""

// Renders a float without a trailing ".0" for whole numbers and without scientific notation for the
// common range, so the literal matches what pgvector expects (Go uses strconv.FormatFloat 'f', -1, 32).
private fun formatFloat(value: Float): String {
    if (value == value.toLong().toFloat()) return value.toLong().toString()
    return value.toString()
}
