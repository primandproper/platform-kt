package com.primandproper.platform.search.text.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Covers the provider-name validation from platform-go's `search/text/config/config_test.go`. */
class TextSearchConfigTest {
    @Test
    fun `elasticsearch provider resolves`() {
        assertEquals(TextSearchProvider.ELASTICSEARCH, TextSearchProvider.fromValue("elasticsearch"))
    }

    @Test
    fun `algolia provider resolves case-insensitively and trimmed`() {
        assertEquals(TextSearchProvider.ALGOLIA, TextSearchProvider.fromValue("  Algolia "))
    }

    @Test
    fun `unknown provider name resolves to null`() {
        assertNull(TextSearchProvider.fromValue("solr"))
    }

    @Test
    fun `config carries the provider`() {
        assertEquals(
            TextSearchProvider.ELASTICSEARCH,
            TextSearchConfig(TextSearchProvider.ELASTICSEARCH).provider,
        )
    }
}
