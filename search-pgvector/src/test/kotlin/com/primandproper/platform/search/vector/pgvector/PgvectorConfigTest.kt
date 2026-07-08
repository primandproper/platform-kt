package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.errors.isError
import com.primandproper.platform.search.vector.DistanceMetric
import com.primandproper.platform.search.vector.ErrInvalidDimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `search/vector/pgvector/config_test.go` and the encode helpers. */
class PgvectorConfigTest {
    @Test
    fun `defaults are cosine and metadata column`() {
        val cfg = PgvectorConfig(dimension = 8)
        assertEquals(DistanceMetric.COSINE, cfg.metric)
        assertEquals("metadata", cfg.metadataColumn)
    }

    @Test
    fun `validate accepts a positive dimension`() {
        PgvectorConfig(dimension = 1).validate()
    }

    @Test
    fun `validate rejects a non-positive dimension`() {
        val err = assertFailsWith<Throwable> { PgvectorConfig(dimension = 0).validate() }
        assertTrue(isError(err, ErrInvalidDimension))
    }

    @Test
    fun `encodeVector renders a pgvector literal`() {
        assertEquals("[1,2.5,3]", encodeVector(floatArrayOf(1f, 2.5f, 3f)))
    }

    @Test
    fun `pgTextArray escapes quotes and backslashes`() {
        assertEquals("""{"a","b\"c","d\\e"}""", pgTextArray(listOf("a", "b\"c", "d\\e")))
    }

    @Test
    fun `quoteIdent doubles embedded quotes`() {
        assertEquals("\"na\"\"me\"", quoteIdent("na\"me"))
    }
}
