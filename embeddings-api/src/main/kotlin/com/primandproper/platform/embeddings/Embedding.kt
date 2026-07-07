package com.primandproper.platform.embeddings

import java.time.Instant

/**
 * The result of embedding a single piece of content. Port of platform-go's `embeddings.Embedding`.
 *
 * It carries provenance ([model], [provider], [generatedAt], [sourceText]) alongside the [vector] so
 * that re-embedding and ETL pipelines can be driven from the stored result alone, exactly as the Go
 * type's doc comment describes.
 *
 * [vector] is a `List<Float>` rather than a `FloatArray`: Go's field is `[]float32`, and a list gives
 * the value-equality a `data class` needs (a `FloatArray`'s identity equality would make the generated
 * `equals`/`hashCode` misleading, the same reason `HttpRequest` is not a data class).
 */
public data class Embedding(
    val vector: List<Float>,
    val sourceText: String,
    val model: String,
    val provider: String,
    val dimensions: Int,
    val generatedAt: Instant,
)
