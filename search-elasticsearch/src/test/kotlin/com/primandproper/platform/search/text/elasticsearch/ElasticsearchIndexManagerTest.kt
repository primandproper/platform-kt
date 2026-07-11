package com.primandproper.platform.search.text.elasticsearch

import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.search.text.StringDocumentCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `search/text/elasticsearch/{elasticsearch,index}_test.go`, run against a fake client. */
class ElasticsearchIndexManagerTest {
    private fun manager(
        client: FakeElasticsearchClient = FakeElasticsearchClient(),
        observer: RecordingObserver? = null,
        indexName: String = "Test",
    ): ElasticsearchIndexManager<String> =
        if (observer != null) {
            ElasticsearchIndexManager(observer, client, StringDocumentCodec, indexName)
        } else {
            ElasticsearchIndexManager(client, StringDocumentCodec, indexName)
        }

    @Test
    fun `ensureIndices creates a missing index using the lowercased name`() =
        runTest {
            val client = FakeElasticsearchClient()
            manager(client, indexName = "Test").ensureIndices()
            assertEquals(listOf("test"), client.createdIndices)
        }

    @Test
    fun `ensureIndices does not recreate an existing index`() =
        runTest {
            val client = FakeElasticsearchClient(existingIndices = mutableSetOf("test"))
            manager(client).ensureIndices()
            assertTrue(client.createdIndices.isEmpty())
        }

    @Test
    fun `Index stores the encoded document under the lowercased index`() =
        runTest {
            val client = FakeElasticsearchClient()
            manager(client).index("id-1", "the-document")
            assertEquals("the-document", client.documents["test/id-1"])
        }

    @Test
    fun `Search builds a multi_match body and decodes each hit`() =
        runTest {
            val client = FakeElasticsearchClient()
            client.searchHits = listOf("doc-a", "doc-b")

            val out = manager(client).search("hello")

            assertEquals(listOf("doc-a", "doc-b"), out)
            assertEquals(
                """{"query":{"multi_match":{"query":"hello","type":"best_fields","fields":["*"]}}}""",
                client.searchBodies.single(),
            )
        }

    @Test
    fun `Search escapes quotes in the query`() =
        runTest {
            val client = FakeElasticsearchClient()
            manager(client).search("""a "quoted" term""")
            assertTrue(client.searchBodies.single().contains("""\"quoted\""""))
        }

    @Test
    fun `Search rejects an empty query`() =
        runTest {
            assertFailsWith<Throwable> { manager().search("") }
                .also { assertTrue(it is EmptyQueryProvidedException) }
        }

    @Test
    fun `Delete removes the document`() =
        runTest {
            val client = FakeElasticsearchClient()
            val m = manager(client)
            m.index("id-1", "d")
            m.delete("id-1")
            assertTrue(client.documents.isEmpty())
        }

    @Test
    fun `Wipe issues a match_all delete-by-query`() =
        runTest {
            val client = FakeElasticsearchClient()
            val m = manager(client)
            m.index("id-1", "d")
            m.wipe()
            assertEquals("""{"query":{"match_all":{}}}""", client.deleteByQueryBodies.single())
            assertTrue(client.documents.isEmpty())
        }

    @Test
    fun `Search records the query and result count on the operation`() =
        runTest {
            val obs = RecordingObserver()
            val client = FakeElasticsearchClient()
            client.searchHits = listOf("only-hit")

            manager(client, obs).search("q")

            val op = obs.operations.last { it.name == "Search" }
            assertTrue(op.ended)
            assertTrue(op.errors.isEmpty())
            assertEquals("q", op.values["search.query"])
            assertEquals(1, op.values["length"])
        }

    @Test
    fun `Index records the engine error on the span and rethrows`() =
        runTest {
            val obs = RecordingObserver()
            val client = FakeElasticsearchClient(failOn = "indexDocument")

            assertFailsWith<RuntimeException> { manager(client, obs).index("id", "d") }

            val op = obs.operations.last { it.name == "Index" }
            assertTrue(op.errors.isNotEmpty())
            assertTrue(op.ended)
        }
}
