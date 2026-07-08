package com.primandproper.platform.search.text.elasticsearch

/**
 * The minimal Elasticsearch command surface [ElasticsearchIndexManager] needs, expressed in raw JSON
 * strings. It exists for the same reason platform-go's backend is written against `esapi` request
 * types: so the index-management logic can be unit-tested against a fake without a live cluster
 * (mirroring `:cache-redis`'s `RedisClient`). [LowLevelElasticsearchClient] is the production adapter
 * over the Elastic JVM client's low-level REST transport.
 *
 * All methods suspend; the production adapter bridges each blocking REST call onto `Dispatchers.IO`.
 */
public interface ElasticsearchClient {
    /** Reports whether [index] already exists (Elasticsearch's `IndicesExists`, 200 vs 404). */
    public suspend fun indexExists(index: String): Boolean

    /** Creates [index] (Elasticsearch's `IndicesCreate`). */
    public suspend fun createIndex(index: String)

    /** Indexes [documentJson] under [id] in [index], overwriting any existing document (`IndexRequest`). */
    public suspend fun indexDocument(
        index: String,
        id: String,
        documentJson: String,
    )

    /**
     * Runs the [queryJson] search body against [index] and returns each hit's raw `_source` JSON, in
     * ranking order. An empty list means no hits.
     */
    public suspend fun search(
        index: String,
        queryJson: String,
    ): List<String>

    /** Deletes the document with [id] from [index]; a missing document is treated as success (idempotent). */
    public suspend fun delete(
        index: String,
        id: String,
    )

    /** Removes every document matching [queryJson] from [index], refreshing immediately (`DeleteByQuery`). */
    public suspend fun deleteByQuery(
        index: String,
        queryJson: String,
    )

    /** Verifies the cluster is reachable (Elasticsearch's `Info`/`Ping`). */
    public suspend fun ping(): Boolean
}
