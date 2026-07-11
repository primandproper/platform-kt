package com.primandproper.platform.embeddings

import java.time.Instant

/**
 * The result of embedding a single piece of content. Port of platform-go's `embeddings.Embedding`.
 *
 * It carries provenance ([model], [provider], [generatedAt], [sourceText]) alongside the [vector] so
 * that re-embedding and ETL pipelines can be driven from the stored result alone, exactly as the Go
 * type's doc comment describes.
 *
 * [vector] is a `FloatArray`, mirroring Go's `[]float32`: a dense embedding is exactly what a primitive
 * float array is for, so it avoids boxing every component the way a `List<Float>` would. A `FloatArray`
 * has identity `equals`/`hashCode`, so this data class overrides both to compare [vector] structurally
 * via `contentEquals`/`contentHashCode` — the same pattern `search.vector.Vector` uses. (`copy` and
 * `componentN` still shallow-copy the array reference, as they do for any array-holding data class.)
 */
public data class Embedding(
    val vector: FloatArray,
    val sourceText: String,
    val model: String,
    val provider: String,
    val dimensions: Int,
    val generatedAt: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Embedding) return false
        return vector.contentEquals(other.vector) &&
            sourceText == other.sourceText &&
            model == other.model &&
            provider == other.provider &&
            dimensions == other.dimensions &&
            generatedAt == other.generatedAt
    }

    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + sourceText.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + dimensions
        result = 31 * result + generatedAt.hashCode()
        return result
    }
}
