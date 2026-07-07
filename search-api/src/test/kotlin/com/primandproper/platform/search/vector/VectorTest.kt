package com.primandproper.platform.search.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Covers the value types and metric parsing from platform-go's `search/vector` package. */
class VectorTest {
    @Test
    fun `metric resolves case-insensitively and trimmed`() {
        assertEquals(DistanceMetric.COSINE, DistanceMetric.fromValue(" Cosine "))
        assertEquals(DistanceMetric.DOT_PRODUCT, DistanceMetric.fromValue("dot"))
        assertEquals(DistanceMetric.EUCLIDEAN, DistanceMetric.fromValue("euclidean"))
    }

    @Test
    fun `unknown metric resolves to null`() {
        assertNull(DistanceMetric.fromValue("manhattan"))
    }

    @Test
    fun `vectors with equal embeddings compare equal`() {
        val a = Vector("id", floatArrayOf(0.1f, 0.2f), "meta")
        val b = Vector("id", floatArrayOf(0.1f, 0.2f), "meta")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `vectors with different embeddings compare unequal`() {
        val a = Vector("id", floatArrayOf(0.1f, 0.2f), "meta")
        val b = Vector("id", floatArrayOf(0.1f, 0.9f), "meta")
        assertNotEquals(a, b)
    }

    @Test
    fun `query request defaults topK to ten`() {
        assertEquals(10, QueryRequest(floatArrayOf(1f)).topK)
    }

    @Test
    fun `query result carries metadata`() {
        val r = QueryResult("id", 0.42f, "meta")
        assertEquals("id", r.id)
        assertEquals(0.42f, r.distance)
        assertEquals("meta", r.metadata)
    }
}
