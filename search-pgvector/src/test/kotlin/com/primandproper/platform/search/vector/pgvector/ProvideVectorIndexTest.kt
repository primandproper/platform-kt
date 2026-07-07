package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.Vector
import com.primandproper.platform.search.vector.config.VectorSearchConfig
import com.primandproper.platform.search.vector.config.VectorSearchProvider
import com.primandproper.platform.search.vector.noop.NoopVectorIndex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `search/vector/config/config_test.go` provider dispatch (`ProvideIndex`). */
class ProvideVectorIndexTest {
    @Test
    fun `pgvector provider builds a manager and migrates the schema`() =
        runTest {
            val exec = FakePgvectorExecutor()
            val index =
                provideVectorIndex(
                    config = VectorSearchConfig(VectorSearchProvider.PGVECTOR),
                    codec = StringMetadataCodec,
                    indexName = "docs",
                    pgvectorConfig = PgvectorConfig(dimension = 3),
                    executor = exec,
                )
            assertTrue(index is PgvectorIndexManager<String>)
            // ensureTable ran during construction.
            assertEquals(1, exec.transactionCount)
        }

    @Test
    fun `pgvector provider without config fails`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                provideVectorIndex(
                    config = VectorSearchConfig(VectorSearchProvider.PGVECTOR),
                    codec = StringMetadataCodec,
                    indexName = "docs",
                    executor = FakePgvectorExecutor(),
                )
            }
        }

    @Test
    fun `pgvector provider without executor fails`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                provideVectorIndex(
                    config = VectorSearchConfig(VectorSearchProvider.PGVECTOR),
                    codec = StringMetadataCodec,
                    indexName = "docs",
                    pgvectorConfig = PgvectorConfig(dimension = 3),
                )
            }
        }

    @Test
    fun `qdrant provider falls back to a noop index (documented seam)`() =
        runTest {
            val index =
                provideVectorIndex(
                    config = VectorSearchConfig(VectorSearchProvider.QDRANT),
                    codec = StringMetadataCodec,
                    indexName = "docs",
                )
            assertTrue(index is NoopVectorIndex<String>)
        }

    @Test
    fun `pgvector provider drives an injected executor`() =
        runTest {
            val exec = FakePgvectorExecutor()
            val index =
                provideVectorIndex(
                    config = VectorSearchConfig(VectorSearchProvider.PGVECTOR),
                    codec = StringMetadataCodec,
                    indexName = "docs",
                    pgvectorConfig = PgvectorConfig(dimension = 3),
                    executor = exec,
                )
            index.upsert(Vector("a", floatArrayOf(1f, 2f, 3f), "m"))
            assertEquals("a", exec.transactionStatements.last().params.first())

            exec.queryRows = listOf(FakeRow(mapOf("id" to "a", "metadata" to "m", "distance" to 0.1)))
            val out = index.query(QueryRequest(floatArrayOf(1f, 2f, 3f)))
            assertEquals("a", out.single().id)
        }
}
