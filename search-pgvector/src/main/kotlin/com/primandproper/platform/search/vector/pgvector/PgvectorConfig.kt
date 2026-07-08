package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.search.vector.DistanceMetric
import com.primandproper.platform.search.vector.ErrInvalidDimension

/**
 * Configures the pgvector-backed vector index. Port of platform-go's `pgvector.Config`.
 *
 * @param dimension the embedding dimension, enforced at table creation via `vector(<dimension>)`; must
 *   match the dimension the upstream model produces and be at least 1.
 * @param metric the nearest-neighbor scoring function; drives the index operator class and query
 *   operator. Defaults to cosine, matching Go's `envDefault:"cosine"`.
 * @param metadataColumn the `jsonb` column storing the per-vector payload. Defaults to `metadata`; must
 *   be a bare identifier (validated when the index is built).
 */
public data class PgvectorConfig(
    val dimension: Int,
    val metric: DistanceMetric = DistanceMetric.COSINE,
    val metadataColumn: String = "metadata",
) {
    /**
     * Validates the config, throwing when [dimension] is non-positive — the analog of Go's
     * `validation.Field(&cfg.Dimension, validation.Required, validation.Min(1))`. The metric is a
     * non-null enum here, so Go's `validation.In(...)` on the metric is satisfied by construction.
     */
    public fun validate() {
        if (dimension < 1) throw ErrInvalidDimension
    }
}
