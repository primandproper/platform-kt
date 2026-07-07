package com.primandproper.platform.search.vector

/**
 * Selects the nearest-neighbor scoring function an index ranks by. Port of platform-go's
 * `vectorsearch.DistanceMetric`. [value] is the wire/string form validated against configuration.
 */
public enum class DistanceMetric(
    public val value: String,
) {
    /** Ranks results by cosine similarity. */
    COSINE("cosine"),

    /** Ranks results by dot product. */
    DOT_PRODUCT("dot"),

    /** Ranks results by Euclidean (L2) distance. */
    EUCLIDEAN("euclidean"),
    ;

    public companion object {
        /** Resolves a metric from its string [value] (trimmed, case-insensitive), or `null` if unknown. */
        public fun fromValue(value: String): DistanceMetric? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * A single indexable point. Port of platform-go's `vectorsearch.Vector[T]`. [T] is the metadata
 * payload type, generic in the same way [Index] is generic over the document payload type; a `null`
 * [metadata] round-trips as the empty payload.
 */
public data class Vector<T : Any>(
    val id: String,
    val embedding: FloatArray,
    val metadata: T? = null,
) {
    // FloatArray has identity equals/hashCode; data-class-generated members would break value
    // comparison of the embedding, so they are overridden to compare it structurally.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector<*>) return false
        return id == other.id && embedding.contentEquals(other.embedding) && metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }
}

/**
 * Describes a top-K nearest-neighbor search. Port of platform-go's `vectorsearch.QueryRequest`.
 *
 * @param embedding the query vector; must match the index dimension.
 * @param topK the number of results to return; backends may cap this at a backend-specific maximum.
 * @param filter an OPAQUE per-provider filter. The pgvector provider interprets it as a SQL fragment
 *   appended to the WHERE clause; other providers interpret it differently. `null` means no filter.
 *   Cross-provider filter portability is intentionally not modeled — callers translate at the call site.
 */
public data class QueryRequest(
    val embedding: FloatArray,
    val topK: Int = 10,
    val filter: Any? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueryRequest) return false
        return topK == other.topK && embedding.contentEquals(other.embedding) && filter == other.filter
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + topK
        result = 31 * result + (filter?.hashCode() ?: 0)
        return result
    }
}

/**
 * A single hit returned from [IndexSearcher.query]. Port of platform-go's `vectorsearch.QueryResult[T]`.
 * The interpretation of [distance] depends on the index's [DistanceMetric]: cosine produces a value
 * in `[0, 2]` where lower is more similar; dot product is unbounded; euclidean is the L2 distance.
 */
public data class QueryResult<T : Any>(
    val id: String,
    val distance: Float,
    val metadata: T? = null,
)

/**
 * The write half of a vector [Index]. Port of platform-go's `vectorsearch.IndexWriter[T]`. `vararg`
 * mirrors Go's variadic `Upsert(ctx, vectors ...Vector[T])` / `Delete(ctx, ids ...string)`.
 */
public interface IndexWriter<T : Any> {
    /** Inserts or replaces [vectors] keyed by id. */
    public suspend fun upsert(vararg vectors: Vector<T>)

    /** Removes vectors by [ids]; missing ids are ignored. */
    public suspend fun delete(vararg ids: String)

    /** Removes all vectors from the index, leaving the index itself in place. */
    public suspend fun wipe()
}

/** The read half of a vector [Index]. Port of platform-go's `vectorsearch.IndexSearcher[T]`. */
public interface IndexSearcher<T : Any> {
    /** Returns the top-K nearest neighbors for [request]'s embedding. */
    public suspend fun query(request: QueryRequest): List<QueryResult<T>>
}

/**
 * A generic vector index, parameterized over the metadata payload type [T]. Port of platform-go's
 * `vectorsearch.Index[T]`; it mirrors [com.primandproper.platform.search.text.Index] in shape.
 * Backend implementations (`:search-pgvector`, and the documented Qdrant seam) live in their own
 * modules and are selected via `provideVectorIndex`.
 */
public interface Index<T : Any> :
    IndexWriter<T>,
    IndexSearcher<T>
