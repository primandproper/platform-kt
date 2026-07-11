package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.search.vector.Index
import com.primandproper.platform.search.vector.config.VectorSearchConfig
import com.primandproper.platform.search.vector.config.VectorSearchProvider
import com.primandproper.platform.search.vector.noop.NoopVectorIndex

/**
 * Builds a vector [Index] for the configured provider. Port of platform-go's
 * `vectorsearchcfg.ProvideIndex`.
 *
 * This factory lives in `:search-pgvector` rather than `:search-api` because it unites the API
 * contract with a concrete backend — wiring it in the API module would force a dependency cycle. It is
 * the analog of Go's `search/vector/config` package sitting above the backend subpackages, and mirrors
 * how `:cache-redis` owns `provideCache`.
 *
 * It is `suspend` because the pgvector backend runs [PgvectorIndexManager.ensureTable] (the schema
 * migration) before returning, exactly as Go's `ProvideIndex` does.
 *
 * @param config the chosen provider.
 * @param codec turns metadata into its stored `jsonb` form and back; used only by the pgvector backend.
 * @param indexName the target table name.
 * @param pgvectorConfig required when [VectorSearchConfig.provider] is
 *   [VectorSearchProvider.PGVECTOR]; ignored otherwise.
 * @param executor required when the provider is pgvector: the SQL command surface
 *   ([JdbcPgvectorExecutor] in production, a fake in tests). Analogous to Go's required `database.Client`.
 *
 * TODO(qdrant): platform-go also dispatches [VectorSearchProvider.QDRANT] to a real
 * `search/vector/qdrant` backend. That backend is out of scope for this port; the provider is accepted
 * but falls back to a [NoopVectorIndex], the same safe default Go uses for an unknown provider. Port
 * the Qdrant backend into a `:search-qdrant` module and extend this dispatch when it lands.
 *
 * TODO(circuitbreaking) / TODO(metrics): see the seams documented on [PgvectorIndexManager].
 */
public suspend fun <T : Any> provideVectorIndex(
    config: VectorSearchConfig,
    codec: MetadataCodec<T>,
    indexName: String,
    pgvectorConfig: PgvectorConfig? = null,
    executor: PgvectorExecutor? = null,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): Index<T> =
    when (config.provider) {
        VectorSearchProvider.PGVECTOR -> {
            val cfg = requireNotNull(pgvectorConfig) { "pgvector provider requires a PgvectorConfig" }
            val sqlExecutor = requireNotNull(executor) { "pgvector provider requires a PgvectorExecutor" }
            cfg.validate()
            PgvectorIndexManager(sqlExecutor, codec, cfg, indexName, logger, tracerProvider).also { it.ensureTable() }
        }
        VectorSearchProvider.QDRANT -> NoopVectorIndex()
    }
