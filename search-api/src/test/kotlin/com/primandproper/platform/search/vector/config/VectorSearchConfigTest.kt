package com.primandproper.platform.search.vector.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Covers the provider-name validation from platform-go's `search/vector/config/config_test.go`. */
class VectorSearchConfigTest {
    @Test
    fun `pgvector provider resolves`() {
        assertEquals(VectorSearchProvider.PGVECTOR, VectorSearchProvider.fromValue("pgvector"))
    }

    @Test
    fun `qdrant provider resolves case-insensitively and trimmed`() {
        assertEquals(VectorSearchProvider.QDRANT, VectorSearchProvider.fromValue("  Qdrant "))
    }

    @Test
    fun `unknown provider name resolves to null`() {
        assertNull(VectorSearchProvider.fromValue("pinecone"))
    }

    @Test
    fun `config carries the provider`() {
        assertEquals(
            VectorSearchProvider.PGVECTOR,
            VectorSearchConfig(VectorSearchProvider.PGVECTOR).provider,
        )
    }
}
