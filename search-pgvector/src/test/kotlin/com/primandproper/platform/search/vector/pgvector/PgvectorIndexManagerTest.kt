package com.primandproper.platform.search.vector.pgvector

import com.primandproper.platform.errors.ErrInvalidIDProvided
import com.primandproper.platform.errors.isError
import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.search.vector.DistanceMetric
import com.primandproper.platform.search.vector.ErrDimensionMismatch
import com.primandproper.platform.search.vector.ErrEmptyEmbedding
import com.primandproper.platform.search.vector.QueryRequest
import com.primandproper.platform.search.vector.Vector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of platform-go's `search/vector/pgvector/pgvector_test.go`, run against a recording fake executor. */
class PgvectorIndexManagerTest {
    private fun manager(
        executor: FakePgvectorExecutor = FakePgvectorExecutor(),
        observer: RecordingObserver? = null,
        dimension: Int = 3,
        metric: DistanceMetric = DistanceMetric.COSINE,
        indexName: String = "docs",
    ): PgvectorIndexManager<String> {
        val config = PgvectorConfig(dimension = dimension, metric = metric)
        return if (observer != null) {
            PgvectorIndexManager(observer, executor, StringMetadataCodec, indexName, config.metadataColumn, metric, dimension)
        } else {
            PgvectorIndexManager(executor, StringMetadataCodec, config, indexName)
        }
    }

    // ---- construction & identifier validation ----

    @Test
    fun `invalid index name is rejected`() {
        val err = assertFailsWith<Throwable> { manager(indexName = "bad name!") }
        assertTrue(isError(err, ErrInvalidIdentifier))
    }

    // ---- ensureTable ----

    @Test
    fun `ensureTable migrates schema in a single transaction`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).ensureTable()

            assertEquals(1, exec.transactionCount)
            val sql = exec.transactionStatements.map { it.sql }
            assertTrue(sql.any { it.startsWith("SELECT pg_advisory_xact_lock") })
            assertTrue(sql.any { it == "CREATE EXTENSION IF NOT EXISTS vector" })
            assertTrue(sql.any { it.contains("""CREATE TABLE IF NOT EXISTS "docs"""") && it.contains("vector(3)") })
            assertTrue(sql.any { it.contains("USING hnsw (embedding vector_cosine_ops)") })
        }

    // ---- upsert ----

    @Test
    fun `Upsert writes each row in one transaction with cast placeholders`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).upsert(
                Vector("a", floatArrayOf(1f, 2f, 3f), "meta-a"),
                Vector("b", floatArrayOf(4f, 5f, 6f), "meta-b"),
            )

            assertEquals(1, exec.transactionCount)
            assertEquals(2, exec.transactionStatements.size)
            val first = exec.transactionStatements.first()
            assertTrue(first.sql.contains("INSERT INTO \"docs\""))
            assertTrue(first.sql.contains("VALUES (?, ?::vector, ?::jsonb)"))
            assertTrue(first.sql.contains("ON CONFLICT (id) DO UPDATE"))
            assertEquals(listOf("a", "[1,2,3]", "meta-a"), first.params)
        }

    @Test
    fun `Upsert with no vectors is a no-op`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).upsert()
            assertEquals(0, exec.transactionCount)
        }

    @Test
    fun `Upsert rejects an empty id`() =
        runTest {
            val err = assertFailsWith<Throwable> { manager().upsert(Vector("", floatArrayOf(1f, 2f, 3f))) }
            assertTrue(isError(err, ErrInvalidIDProvided))
        }

    @Test
    fun `Upsert rejects an empty embedding`() =
        runTest {
            val err = assertFailsWith<Throwable> { manager().upsert(Vector("a", floatArrayOf())) }
            assertTrue(isError(err, ErrEmptyEmbedding))
        }

    @Test
    fun `Upsert rejects a dimension mismatch`() =
        runTest {
            val err = assertFailsWith<Throwable> { manager().upsert(Vector("a", floatArrayOf(1f, 2f))) }
            assertTrue(isError(err, ErrDimensionMismatch))
        }

    // ---- delete & wipe ----

    @Test
    fun `Delete issues an ANY text-array statement`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).delete("a", "b")

            val stmt = exec.executed.single()
            assertEquals("DELETE FROM \"docs\" WHERE id = ANY(?::text[])", stmt.sql)
            assertEquals(listOf("{\"a\",\"b\"}"), stmt.params)
        }

    @Test
    fun `Delete with no ids is a no-op`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).delete()
            assertTrue(exec.executed.isEmpty())
        }

    @Test
    fun `Wipe truncates the table`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).wipe()
            assertEquals("TRUNCATE TABLE \"docs\"", exec.executed.single().sql)
        }

    // ---- query ----

    @Test
    fun `Query builds the distance SQL and maps rows`() =
        runTest {
            val exec = FakePgvectorExecutor()
            exec.queryRows =
                listOf(
                    FakeRow(mapOf("id" to "a", "metadata" to "meta-a", "distance" to 0.10)),
                    FakeRow(mapOf("id" to "b", "metadata" to null, "distance" to 0.25)),
                )

            val out = manager(exec).query(QueryRequest(floatArrayOf(1f, 2f, 3f), topK = 5))

            val stmt = exec.queried.single()
            assertEquals(
                "SELECT id, \"metadata\", embedding <=> ?::vector AS distance FROM \"docs\" ORDER BY distance ASC LIMIT ?",
                stmt.sql,
            )
            assertEquals(listOf("[1,2,3]", 5), stmt.params)

            assertEquals(2, out.size)
            assertEquals("a", out[0].id)
            assertEquals(0.10f, out[0].distance)
            assertEquals("meta-a", out[0].metadata)
            assertNull(out[1].metadata) // null jsonb decodes to "no metadata"
        }

    @Test
    fun `Query defaults a non-positive topK to ten`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).query(QueryRequest(floatArrayOf(1f, 2f, 3f), topK = 0))
            assertEquals(listOf("[1,2,3]", 10), exec.queried.single().params)
        }

    @Test
    fun `Query appends a string filter to the WHERE clause`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec).query(QueryRequest(floatArrayOf(1f, 2f, 3f), filter = "metadata->>'kind' = 'doc'"))
            assertTrue(exec.queried.single().sql.contains("FROM \"docs\" WHERE metadata->>'kind' = 'doc' ORDER BY"))
        }

    @Test
    fun `Query rejects a non-string filter`() =
        runTest {
            val err = assertFailsWith<Throwable> { manager().query(QueryRequest(floatArrayOf(1f, 2f, 3f), filter = 42)) }
            assertTrue(isError(err, ErrInvalidFilter))
        }

    @Test
    fun `Query uses the metric operator`() =
        runTest {
            val exec = FakePgvectorExecutor()
            manager(exec, metric = DistanceMetric.EUCLIDEAN).query(QueryRequest(floatArrayOf(1f, 2f, 3f)))
            assertTrue(exec.queried.single().sql.contains("embedding <-> ?::vector"))
        }

    @Test
    fun `Query rejects a dimension mismatch`() =
        runTest {
            val err = assertFailsWith<Throwable> { manager().query(QueryRequest(floatArrayOf(1f))) }
            assertTrue(isError(err, ErrDimensionMismatch))
        }

    // ---- observability ----

    @Test
    fun `Query records the index name and result count`() =
        runTest {
            val obs = RecordingObserver()
            val exec = FakePgvectorExecutor()
            exec.queryRows = listOf(FakeRow(mapOf("id" to "a", "metadata" to null, "distance" to 0.1)))

            manager(exec, obs).query(QueryRequest(floatArrayOf(1f, 2f, 3f)))

            val op = obs.operations.last { it.name == "Query" }
            assertTrue(op.ended)
            assertTrue(op.errors.isEmpty())
            assertEquals("docs", op.values["search.index"])
            assertEquals(1, op.values["length"])
        }

    @Test
    fun `Upsert records the SQL error on the span and rethrows`() =
        runTest {
            val obs = RecordingObserver()
            val exec = FakePgvectorExecutor(failOn = "tx.execute")

            assertFailsWith<RuntimeException> {
                manager(exec, obs).upsert(Vector("a", floatArrayOf(1f, 2f, 3f)))
            }

            val op = obs.operations.last { it.name == "Upsert" }
            assertTrue(op.errors.isNotEmpty())
            assertTrue(op.ended)
        }
}
