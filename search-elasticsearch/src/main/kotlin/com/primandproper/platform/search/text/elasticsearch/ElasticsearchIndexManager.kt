package com.primandproper.platform.search.text.elasticsearch

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.search.text.DocumentCodec
import com.primandproper.platform.search.text.Index

/** Indicates an empty query was provided to [ElasticsearchIndexManager.search]. Mirrors Go's `ErrEmptyQueryProvided`. */
public val ErrEmptyQueryProvided: PlatformException = PlatformException("empty search query provided")

/**
 * An Elasticsearch-backed text [Index]. Port of platform-go's `search/text/elasticsearch.indexManager[T]`.
 *
 * The engine calls go through an injected [ElasticsearchClient] — [LowLevelElasticsearchClient] in
 * production, a fake in tests — so the index-management logic here is exercised without a live
 * cluster, exactly as `:cache-redis` tests `RedisCache` against a fake `RedisClient`. Documents cross
 * the boundary as JSON via the injected [DocumentCodec], mirroring Go's `json.Marshal`/`json.Unmarshal`.
 *
 * Every method opens an [Observer] span recording the document id (or query / result count) and the
 * index name, mirroring `sm.o11y.Begin(ctx)` / `op.Set(...)`; the `span` scope records and rethrows
 * any thrown engine error exactly once.
 *
 * The index name is lowercased once at construction — Elasticsearch index names must be lowercase, and
 * Go normalizes it so create, existence-check, index, delete, and search all target the same name.
 *
 * TODO(circuitbreaking): platform-go wraps every operation with a `circuitbreaking.CircuitBreaker`
 * (short-circuiting while open, counting successes/failures). That module is outside this port's
 * dependency set, so the breaker is a documented seam — inject one here once available downstream, the
 * same descope `:cache-redis` makes.
 */
public class ElasticsearchIndexManager<T : Any> internal constructor(
    private val o11y: Observer,
    private val client: ElasticsearchClient,
    private val codec: DocumentCodec<T>,
    indexName: String,
) : Index<T> {
    /**
     * @param client the engine command surface; [LowLevelElasticsearchClient] in production, a fake in tests.
     * @param codec turns a document into its stored JSON form and each hit's `_source` back into a `T`.
     * @param indexName the target index; lowercased to satisfy Elasticsearch's naming rule.
     * @param logger optional root logger; defaults to a noop logger, matching Go's `NewIndexManager(nil, ...)`.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        client: ElasticsearchClient,
        codec: DocumentCodec<T>,
        indexName: String,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(Observer("search_${indexName.lowercase()}", logger, tracerProvider), client, codec, indexName)

    private val index: String = indexName.lowercase()

    /**
     * Creates the index if it does not already exist, mirroring Go's `ensureIndices`. Called by
     * [provideTextIndex] after construction (a `suspend` context the constructor cannot provide);
     * safe to call repeatedly.
     */
    public suspend fun ensureIndices() {
        o11y.span("EnsureIndices") {
            set(INDEX_NAME_KEY, index)
            if (!client.indexExists(index)) {
                client.createIndex(index)
            }
        }
    }

    override suspend fun index(
        id: String,
        value: Any,
    ) {
        o11y.span("Index") {
            set(ID_KEY, id)
            set(INDEX_NAME_KEY, index)
            logger.debug("adding to index")
            client.indexDocument(index, id, codec.encode(value))
        }
    }

    override suspend fun search(query: String): List<T> =
        o11y.span("Search") {
            set(Keys.SEARCH_QUERY, query)
            if (query.isEmpty()) {
                throw ErrEmptyQueryProvided
            }
            val sources = client.search(index, multiMatchQuery(query))
            val results = sources.map { codec.decode(it) }
            set(INDEX_NAME_KEY, index)
            set(Keys.LENGTH, results.size)
            results
        }

    override suspend fun delete(id: String) {
        o11y.span("Delete") {
            set(ID_KEY, id)
            set(INDEX_NAME_KEY, index)
            client.delete(index, id)
            logger.debug("removed from index")
        }
    }

    override suspend fun wipe() {
        o11y.span("Wipe") {
            set(INDEX_NAME_KEY, index)
            client.deleteByQuery(index, MATCH_ALL_QUERY)
        }
    }

    private companion object {
        // platform-go records these on `keys.IndexNameKey` / `op.Set("id", id)`; platform-kt's
        // observability Keys has no index-name key, so the string is inlined here.
        const val INDEX_NAME_KEY = "search.index"
        const val ID_KEY = "id"
    }
}
