package com.primandproper.platform.search.text.elasticsearch

import com.primandproper.platform.search.text.StringDocumentCodec
import com.primandproper.platform.search.text.config.TextSearchConfig
import com.primandproper.platform.search.text.config.TextSearchProvider
import com.primandproper.platform.search.text.noop.NoopIndex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `search/text/config/config_test.go` provider dispatch (`ProvideIndex`). */
class ProvideTextIndexTest {
    @Test
    fun `elasticsearch provider builds a manager and ensures the index`() =
        runTest {
            val client = FakeElasticsearchClient()
            val index =
                provideTextIndex(
                    config = TextSearchConfig(TextSearchProvider.ELASTICSEARCH),
                    codec = StringDocumentCodec,
                    indexName = "docs",
                    client = client,
                )
            assertTrue(index is ElasticsearchIndexManager<String>)
            // ensureIndices ran during construction, creating the missing index.
            assertEquals(listOf("docs"), client.createdIndices)
        }

    @Test
    fun `elasticsearch provider without config or client fails`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                provideTextIndex(
                    config = TextSearchConfig(TextSearchProvider.ELASTICSEARCH),
                    codec = StringDocumentCodec,
                    indexName = "docs",
                )
            }
        }

    @Test
    fun `algolia provider falls back to a noop index (documented seam)`() =
        runTest {
            val index =
                provideTextIndex(
                    config = TextSearchConfig(TextSearchProvider.ALGOLIA),
                    codec = StringDocumentCodec,
                    indexName = "docs",
                )
            assertTrue(index is NoopIndex<String>)
        }

    @Test
    fun `elasticsearch provider drives an injected client`() =
        runTest {
            val client = FakeElasticsearchClient()
            client.searchHits = listOf("hit")
            val index =
                provideTextIndex(
                    config = TextSearchConfig(TextSearchProvider.ELASTICSEARCH),
                    codec = StringDocumentCodec,
                    indexName = "docs",
                    client = client,
                )
            index.index("id", "doc")
            assertEquals("doc", client.documents["docs/id"])
            assertEquals(listOf("hit"), index.search("q"))
        }
}
