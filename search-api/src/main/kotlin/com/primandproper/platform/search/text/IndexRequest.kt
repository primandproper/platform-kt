package com.primandproper.platform.search.text

/**
 * A queued index-maintenance instruction, as carried on the wire between a producer and an indexing
 * worker. Port of platform-go's `textsearch.IndexRequest`. The field names line up with the Go JSON
 * tags (`id`, `rowID`, `type`, `testID`, `delete`) so a payload produced by either runtime round-trips
 * against the other.
 *
 * @param requestId the request's own id (`json:"id"`).
 * @param rowId the id of the row to (re)index or delete.
 * @param indexType names the target index/type.
 * @param testId an optional correlation id used only in tests (`json:"testID,omitempty"`).
 * @param delete when true, the row should be removed from the index rather than indexed.
 */
public data class IndexRequest(
    val requestId: String,
    val rowId: String,
    val indexType: String,
    val testId: String? = null,
    val delete: Boolean = false,
)
