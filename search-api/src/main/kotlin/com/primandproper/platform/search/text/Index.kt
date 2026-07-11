package com.primandproper.platform.search.text

/**
 * The read half of a text search index. Port of platform-go's `textsearch.IndexSearcher[T]`.
 *
 * Go's `Search` returns `(ids []*T, err error)` — a slice of decoded documents. Kotlin has no
 * `(value, error)` split, so [search] returns `List<T>` and signals failure by throwing. [T] is
 * bound to `Any` so decoded documents are non-null, matching how Go dereferences each `*T` hit — a
 * SAM interface, since `search` is its only method.
 */
public fun interface IndexSearcher<T : Any> {
    /** Runs [query] against the index and returns the matching documents (possibly empty). */
    public suspend fun search(query: String): List<T>
}

/**
 * The write/management half of a text search index. Port of platform-go's `textsearch.IndexManager`.
 *
 * Typed to the index's document type [T]: Go's `Index(ctx, id, value any)` takes `any`, but that
 * hole let a `User` be indexed into an `Index<Product>`, so platform-kt narrows it — heterogeneous
 * indexing is no longer a goal. The stored form is produced by a [DocumentCodec] at the backend
 * boundary, mirroring how Go json-encodes the value before shipping it to the engine.
 */
public interface IndexManager<T : Any> {
    /** Indexes [value] under [id], overwriting any existing document with that id. */
    public suspend fun index(
        id: String,
        value: T,
    )

    /** Removes the document with [id]; idempotent, so deleting an absent id is not an error. */
    public suspend fun delete(id: String)

    /** Removes every document from the index, leaving the index itself in place. */
    public suspend fun wipe()
}

/**
 * A generic text search index — the union of its read and write halves. Port of platform-go's
 * `textsearch.Index[T]`. Backend implementations (`:search-elasticsearch`, and the documented
 * Algolia seam) live in their own modules and are selected via `provideTextIndex`.
 */
public interface Index<T : Any> :
    IndexSearcher<T>,
    IndexManager<T>
