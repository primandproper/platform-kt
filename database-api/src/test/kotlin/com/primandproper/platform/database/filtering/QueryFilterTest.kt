package com.primandproper.platform.database.filtering

import com.primandproper.platform.observability.NoopLogger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Port of platform-go's `database/filtering/query_filter_test.go`. */
class QueryFilterTest {
    private val ts = Instant.parse("2026-07-07T12:00:00Z")

    @Test
    fun `DefaultQueryFilter uses the default limit and ascending sort`() {
        val qf = defaultQueryFilter()
        assertEquals(DEFAULT_QUERY_FILTER_LIMIT, qf.maxResponseSize)
        assertEquals(SORT_ASCENDING, qf.sortBy)
    }

    @Test
    fun `FromParams overrides set fields`() {
        val params =
            mapOf(
                QUERY_KEY_CURSOR to listOf("cur"),
                QUERY_KEY_LIMIT to listOf(MAX_QUERY_FILTER_LIMIT.toString()),
                QUERY_KEY_CREATED_BEFORE to listOf(ts.toString()),
                QUERY_KEY_CREATED_AFTER to listOf(ts.toString()),
                QUERY_KEY_UPDATED_BEFORE to listOf(ts.toString()),
                QUERY_KEY_UPDATED_AFTER to listOf(ts.toString()),
                QUERY_KEY_SORT_BY to listOf("desc"),
                QUERY_KEY_INCLUDE_ARCHIVED to listOf("true"),
            )

        val qf = QueryFilter()
        qf.fromParams(params)

        assertEquals(
            QueryFilter(
                sortBy = SORT_DESCENDING,
                createdAfter = ts,
                createdBefore = ts,
                updatedAfter = ts,
                updatedBefore = ts,
                maxResponseSize = MAX_QUERY_FILTER_LIMIT,
                includeArchived = true,
                cursor = "cur",
            ),
            qf,
        )

        qf.fromParams(mapOf(QUERY_KEY_SORT_BY to listOf("asc")))
        assertEquals(SORT_ASCENDING, qf.sortBy)
    }

    @Test
    fun `FromParams clamps the limit to the maximum`() {
        val qf = QueryFilter()
        qf.fromParams(mapOf(QUERY_KEY_LIMIT to listOf("9999")))
        assertEquals(MAX_QUERY_FILTER_LIMIT, qf.maxResponseSize)
    }

    @Test
    fun `SetCursor sets and ignores null`() {
        val qf = QueryFilter()
        qf.setCursor("here")
        assertEquals("here", qf.cursor)
        qf.setCursor(null)
        assertEquals("here", qf.cursor)
    }

    @Test
    fun `ToValues serializes set fields`() {
        val qf =
            QueryFilter(
                sortBy = SORT_DESCENDING,
                createdBefore = ts,
                maxResponseSize = MAX_QUERY_FILTER_LIMIT,
                includeArchived = true,
                cursor = "cur",
            )
        val v = qf.toValues()

        assertEquals(listOf("cur"), v[QUERY_KEY_CURSOR])
        assertEquals(listOf("250"), v[QUERY_KEY_LIMIT])
        assertEquals(listOf(SORT_DESCENDING), v[QUERY_KEY_SORT_BY])
        assertEquals(listOf(ts.toString()), v[QUERY_KEY_CREATED_BEFORE])
        assertEquals(listOf("true"), v[QUERY_KEY_INCLUDE_ARCHIVED])
    }

    @Test
    fun `ToValues on a null filter yields the default values`() {
        val qf: QueryFilter? = null
        assertEquals(defaultQueryFilter().toValues(), qf.toValues())
    }

    @Test
    fun `ToPagination carries cursor and max response size`() {
        val qf = QueryFilter(cursor = "cur", maxResponseSize = MAX_QUERY_FILTER_LIMIT)
        val p = qf.toPagination()
        assertEquals("cur", p.cursor)
        assertEquals(MAX_QUERY_FILTER_LIMIT, p.maxResponseSize)
    }

    @Test
    fun `ToPagination on a null filter is non-null`() {
        val qf: QueryFilter? = null
        assertNotNull(qf.toPagination())
    }

    @Test
    fun `extractQueryFilter restores the default limit when zero`() {
        val qf = extractQueryFilter(mapOf(QUERY_KEY_CURSOR to listOf("cur"), QUERY_KEY_LIMIT to listOf("0")))
        assertEquals("cur", qf.cursor)
        assertEquals(DEFAULT_QUERY_FILTER_LIMIT, qf.maxResponseSize)
        assertEquals(SORT_ASCENDING, qf.sortBy)
    }

    @Test
    fun `NewQueryFilteredResult sets the next cursor from the last row`() {
        val qf = QueryFilter(cursor = "prev", maxResponseSize = MAX_QUERY_FILTER_LIMIT)
        val result = newQueryFilteredResult(listOf("a", "b"), 2, 2, { it }, qf)

        assertEquals(listOf("a", "b"), result.data)
        assertEquals("b", result.pagination.cursor)
        assertEquals("prev", result.pagination.previousCursor)
        assertEquals(MAX_QUERY_FILTER_LIMIT, result.pagination.maxResponseSize)
        assertEquals(2L, result.pagination.filteredCount)
        assertEquals(2L, result.pagination.totalCount)
        assertEquals(qf, result.pagination.appliedQueryFilter)
    }

    @Test
    fun `NewQueryFilteredResult with empty data has an empty cursor`() {
        val qf = QueryFilter(cursor = "prev", maxResponseSize = MAX_QUERY_FILTER_LIMIT)
        val result = newQueryFilteredResult(emptyList<String>(), 0, 0, { it }, qf)
        assertEquals("", result.pagination.cursor)
        assertEquals("prev", result.pagination.previousCursor)
    }

    @Test
    fun `NewQueryFilteredResult with no input cursor has an empty previous cursor`() {
        val qf = QueryFilter(maxResponseSize = MAX_QUERY_FILTER_LIMIT)
        val result = newQueryFilteredResult(listOf("a", "b"), 2, 2, { it }, qf)
        assertEquals("b", result.pagination.cursor)
        assertEquals("", result.pagination.previousCursor)
    }

    @Test
    fun `AttachToLogger returns a logger for a filter and for null`() {
        assertNotNull(QueryFilter(cursor = "cur", sortBy = SORT_DESCENDING).attachToLogger(NoopLogger))
        val nilFilter: QueryFilter? = null
        assertNotNull(nilFilter.attachToLogger(NoopLogger))
    }
}
