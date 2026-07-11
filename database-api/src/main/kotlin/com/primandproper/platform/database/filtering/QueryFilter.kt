package com.primandproper.platform.database.filtering

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import java.time.Instant
import java.time.format.DateTimeParseException

/*
 * List-query filtering and pagination. Port of platform-go's `database/filtering` package.
 *
 * `url.Values` (Go's `map[string][]string`) has no JVM analog in the standard library, so the
 * request-facing helpers take and return a `Map<String, List<String>>`. Go parses a filter straight
 * out of an `*http.Request`; this port has no server request type, so [extractQueryFilter] takes the
 * already-parsed query map instead — the same values, without dragging in an HTTP dependency.
 *
 * Timestamps use [java.time.Instant] and ISO-8601 (`Instant.toString()` / `Instant.parse`), the JVM
 * equivalent of Go's `time.RFC3339Nano`.
 */

/** The maximum number of results a list query may request. Port of Go's `MaxQueryFilterLimit`. */
public const val MAX_QUERY_FILTER_LIMIT: Int = 250

/** The default page size when a query does not specify one. Port of Go's `DefaultQueryFilterLimit`. */
public const val DEFAULT_QUERY_FILTER_LIMIT: Int = 50

/** Ascending sort marker, the analog of Go's `SortAscending` (`*"asc"`). */
public const val SORT_ASCENDING: String = "asc"

/** Descending sort marker, the analog of Go's `SortDescending` (`*"desc"`). */
public const val SORT_DESCENDING: String = "desc"

// Query-parameter keys, ported verbatim from Go's `QueryKey*` constants.
public const val QUERY_KEY_LIMIT: String = "limit"
public const val QUERY_KEY_CURSOR: String = "cursor"
public const val QUERY_KEY_CREATED_BEFORE: String = "createdBefore"
public const val QUERY_KEY_CREATED_AFTER: String = "createdAfter"
public const val QUERY_KEY_UPDATED_BEFORE: String = "updatedBefore"
public const val QUERY_KEY_UPDATED_AFTER: String = "updatedAfter"
public const val QUERY_KEY_INCLUDE_ARCHIVED: String = "includeArchived"
public const val QUERY_KEY_SORT_BY: String = "sortBy"

/** Attribute/log key used when a filter is `null`. Port of Go's `keys.FilterIsNilKey`. */
public const val FILTER_IS_NIL_KEY: String = "filter_is_nil"

/**
 * A pagination cursor and its counts. Port of Go's `filtering.Pagination`. `maxResponseSize` is Go's
 * `uint8`; the counts are Go's `uint64` mapped to [Long].
 */
public data class Pagination(
    var appliedQueryFilter: QueryFilter? = null,
    var cursor: String = "",
    var previousCursor: String = "",
    var filteredCount: Long = 0,
    var totalCount: Long = 0,
    var maxResponseSize: Int = 0,
)

/**
 * The filters a caller may apply to a list query. Port of Go's `filtering.QueryFilter`, whose fields
 * are pointers (nil = "not set"); here they are Kotlin nullables. The mutating helpers ([fromParams],
 * [setCursor]) mirror Go's pointer-receiver mutation, while the nil-tolerant readers ([toValues],
 * [toPagination], [attachToLogger]) are extension functions on `QueryFilter?` so a `null` filter
 * behaves like Go's `(*QueryFilter)(nil)` calls.
 */
public data class QueryFilter(
    var sortBy: String? = null,
    var createdAfter: Instant? = null,
    var createdBefore: Instant? = null,
    var updatedAfter: Instant? = null,
    var updatedBefore: Instant? = null,
    var maxResponseSize: Int? = null,
    var includeArchived: Boolean? = null,
    var cursor: String? = null,
) {
    /**
     * Overrides set fields from parsed query values, ignoring params that are absent or unparseable —
     * the same lenient merge Go's `FromParams` performs. [params] is the `url.Values` analog
     * (`Map<String, List<String>>`).
     */
    public fun fromParams(params: Map<String, List<String>>) {
        params.first(QUERY_KEY_CURSOR)?.takeIf { it.isNotEmpty() }?.let { cursor = it }

        params.first(QUERY_KEY_LIMIT)?.toLongOrNull()?.let {
            maxResponseSize = it.coerceIn(0, MAX_QUERY_FILTER_LIMIT.toLong()).toInt()
        }

        parseInstant(params.first(QUERY_KEY_CREATED_BEFORE))?.let { createdBefore = it }
        parseInstant(params.first(QUERY_KEY_CREATED_AFTER))?.let { createdAfter = it }
        parseInstant(params.first(QUERY_KEY_UPDATED_BEFORE))?.let { updatedBefore = it }
        parseInstant(params.first(QUERY_KEY_UPDATED_AFTER))?.let { updatedAfter = it }

        params.first(QUERY_KEY_INCLUDE_ARCHIVED)?.toBooleanStrictOrNull()?.let { includeArchived = it }

        when (params.first(QUERY_KEY_SORT_BY)?.lowercase()) {
            SORT_ASCENDING -> sortBy = SORT_ASCENDING
            SORT_DESCENDING -> sortBy = SORT_DESCENDING
        }
    }

    /**
     * Sets the cursor, ignoring a `null` argument (Go's `SetCursor`). `@JvmName` avoids the JVM
     * signature clash with the generated `cursor` property setter — Kotlin callers still use `setCursor`.
     */
    @JvmName("applyCursor")
    public fun setCursor(cursor: String?) {
        if (cursor != null) this.cursor = cursor
    }
}

/** Builds the default query filter (page size [DEFAULT_QUERY_FILTER_LIMIT], ascending). Port of `DefaultQueryFilter`. */
public fun defaultQueryFilter(): QueryFilter = QueryFilter(maxResponseSize = DEFAULT_QUERY_FILTER_LIMIT, sortBy = SORT_ASCENDING)

/** Attaches a filter's set values to [logger], returning the enriched logger. Port of `AttachToLogger`. */
public fun QueryFilter?.attachToLogger(logger: Logger = NoopLogger): Logger {
    var l = logger.clone()
    if (this == null) {
        return l.withValue(FILTER_IS_NIL_KEY, true)
    }
    cursor?.let { l = l.withValue(QUERY_KEY_CURSOR, it) }
    maxResponseSize?.let { l = l.withValue(QUERY_KEY_LIMIT, it) }
    sortBy?.let { l = l.withValue(QUERY_KEY_SORT_BY, it) }
    createdBefore?.let { l = l.withValue(QUERY_KEY_CREATED_BEFORE, it) }
    createdAfter?.let { l = l.withValue(QUERY_KEY_CREATED_AFTER, it) }
    updatedBefore?.let { l = l.withValue(QUERY_KEY_UPDATED_BEFORE, it) }
    updatedAfter?.let { l = l.withValue(QUERY_KEY_UPDATED_AFTER, it) }
    return l
}

/** Serializes a filter back to query values. A `null` filter yields the default filter's values. Port of `ToValues`. */
public fun QueryFilter?.toValues(): Map<String, List<String>> {
    if (this == null) return defaultQueryFilter().toValues()

    val v = linkedMapOf<String, List<String>>()
    cursor?.let { v[QUERY_KEY_CURSOR] = listOf(it) }
    maxResponseSize?.let { v[QUERY_KEY_LIMIT] = listOf(it.toString()) }
    sortBy?.let { v[QUERY_KEY_SORT_BY] = listOf(it) }
    createdBefore?.let { v[QUERY_KEY_CREATED_BEFORE] = listOf(it.toString()) }
    createdAfter?.let { v[QUERY_KEY_CREATED_AFTER] = listOf(it.toString()) }
    updatedBefore?.let { v[QUERY_KEY_UPDATED_BEFORE] = listOf(it.toString()) }
    updatedAfter?.let { v[QUERY_KEY_UPDATED_AFTER] = listOf(it.toString()) }
    includeArchived?.let { v[QUERY_KEY_INCLUDE_ARCHIVED] = listOf(it.toString()) }
    return v
}

/** Derives a [Pagination] from a filter. A `null` filter uses the default filter. Port of `ToPagination`. */
public fun QueryFilter?.toPagination(): Pagination {
    if (this == null) return defaultQueryFilter().toPagination()
    val p = Pagination()
    cursor?.let { p.cursor = it }
    maxResponseSize?.let { p.maxResponseSize = it }
    return p
}

/**
 * Extracts a filter from parsed query values, starting from [defaultQueryFilter] and restoring the
 * default page size when the query asked for zero. Port of Go's `ExtractQueryFilterFromRequest`, with
 * the already-parsed query map standing in for the `*http.Request`.
 */
public fun extractQueryFilter(params: Map<String, List<String>>): QueryFilter {
    val qf = defaultQueryFilter()
    qf.fromParams(params)
    if (qf.maxResponseSize == 0) {
        qf.maxResponseSize = DEFAULT_QUERY_FILTER_LIMIT
    }
    return qf
}

/**
 * A page of results with its pagination metadata. Port of Go's generic `filtering.QueryFilteredResult[T]`.
 * Go stores `[]*T`; here it is a `List<T>`, and [idExtractor]/the data are non-pointer.
 */
public data class QueryFilteredResult<T>(
    var data: List<T>,
    var pagination: Pagination,
)

/**
 * Builds a [QueryFilteredResult]: carries the counts, preserves the input cursor as the previous
 * cursor, and sets the next cursor from the last row's id (empty when there are no rows). Port of Go's
 * `NewQueryFilteredResult`.
 */
public fun <T> newQueryFilteredResult(
    data: List<T>,
    filteredCount: Long,
    totalCount: Long,
    idExtractor: (T) -> String,
    filter: QueryFilter?,
): QueryFilteredResult<T> {
    val pagination = filter.toPagination()
    pagination.filteredCount = filteredCount
    pagination.totalCount = totalCount
    pagination.appliedQueryFilter = filter

    filter?.cursor?.let { pagination.previousCursor = it }
    pagination.cursor = if (data.isNotEmpty()) idExtractor(data.last()) else ""

    return QueryFilteredResult(data, pagination)
}

private fun Map<String, List<String>>.first(key: String): String? = this[key]?.firstOrNull()

private fun parseInstant(value: String?): Instant? {
    if (value.isNullOrEmpty()) return null
    return try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
