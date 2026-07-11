package com.primandproper.platform.uploads.s3

import com.primandproper.platform.uploads.Attributer
import com.primandproper.platform.uploads.Lister
import com.primandproper.platform.uploads.RangeReader
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.UrlSigner
import com.primandproper.platform.uploads.listAll
import com.primandproper.platform.uploads.objectstorage.Uploader
import com.primandproper.platform.uploads.readBytes
import com.primandproper.platform.uploads.saveBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of the S3 behavior in platform-go's `objectstorage` tests, exercised against an in-memory fake client. */
class S3BucketTest {
    private fun uploader(client: FakeS3Client = FakeS3Client()): Uploader = Uploader(S3Bucket(client), "test-bucket")

    @Test
    fun `save then read round-trips through the client`() =
        runTest {
            val client = FakeS3Client()
            val u = uploader(client)
            u.saveBytes("dir/obj", "hello".toByteArray(), SaveOptions(contentType = "text/plain"))
            assertContentEquals("hello".toByteArray(), u.readBytes("dir/obj"))
        }

    @Test
    fun `exists reflects puts and deletes`() =
        runTest {
            val u = uploader()
            assertFalse(u.exists("k"))
            u.saveBytes("k", "v".toByteArray())
            assertTrue(u.exists("k"))
            u.delete("k")
            assertFalse(u.exists("k"))
        }

    @Test
    fun `attributes maps the head response`() =
        runTest {
            val u = uploader()
            u.saveBytes("k", "abcde".toByteArray(), SaveOptions(contentType = "image/png", cacheControl = "max-age=30"))
            val attrs = (u as Attributer).attributes("k")
            assertEquals("image/png", attrs.contentType)
            assertEquals("max-age=30", attrs.cacheControl)
            assertEquals(5, attrs.size)
            assertEquals("\"5\"", attrs.etag)
        }

    @Test
    fun `attributes on a missing key throws`() =
        runTest {
            assertFailsWith<NoSuchElementException> { (uploader() as Attributer).attributes("absent") }
        }

    @Test
    fun `openRange builds a bounded range header`() =
        runTest {
            val u = uploader()
            u.saveBytes("k", "0123456789".toByteArray())
            val slice = (u as RangeReader).openRange("k", 3, 4).use { it.readBytes() }
            assertContentEquals("3456".toByteArray(), slice)
        }

    @Test
    fun `a zero-length range short-circuits to an empty read without an inverted range header`() =
        runTest {
            val client = FakeS3Client()
            val u = uploader(client)
            u.saveBytes("k", "0123456789".toByteArray())

            val slice = (u as RangeReader).openRange("k", 5, 0).use { it.readBytes() }

            assertTrue(slice.isEmpty())
            // Before the fix this emitted an inverted `bytes=5-4` Range header (S3 answers 416); the fix
            // must short-circuit before ever asking the client, so no getObject request is issued.
            assertTrue(client.getObjectRequests.isEmpty())
        }

    @Test
    fun `list paginates across continuation tokens`() =
        runTest {
            // A page size of 1 forces the bucket to follow continuation tokens across every object.
            val u = uploader(FakeS3Client(pageSize = 1))
            u.saveBytes("p/a", "1".toByteArray())
            u.saveBytes("p/b", "2".toByteArray())
            u.saveBytes("p/c", "3".toByteArray())
            u.saveBytes("other", "x".toByteArray())

            val paths = listAll(u as Lister, "p/").map { it.path }.toSet()
            assertEquals(setOf("p/a", "p/b", "p/c"), paths)
        }

    @Test
    fun `a client error propagates through the uploader`() =
        runTest {
            // Span-level error recording is the Uploader's concern (covered in uploads-api); here we
            // assert the S3 client's failure surfaces through the read path rather than being swallowed.
            val u = uploader(FakeS3Client(failOn = "get"))
            assertFailsWith<RuntimeException> { u.open("k") }
        }

    @Test
    fun `signing is a documented seam`() =
        runTest {
            assertFailsWith<UnsupportedOperationException> { (uploader() as UrlSigner).signedUrl("k") }
        }

    @Test
    fun `newS3UploadManager rejects a non-s3 provider without a client`() {
        assertFailsWith<UnsupportedOperationException> {
            newS3UploadManager(
                com.primandproper.platform.uploads.objectstorage.StorageConfig(
                    bucketName = "b",
                    provider = com.primandproper.platform.uploads.objectstorage.StorageProvider.MEMORY,
                ),
            )
        }
    }

    @Test
    fun `newS3UploadManager wires an injected client`() =
        runTest {
            val u =
                newS3UploadManager(
                    com.primandproper.platform.uploads.objectstorage.StorageConfig(
                        bucketName = "b",
                        provider = com.primandproper.platform.uploads.objectstorage.StorageProvider.S3,
                    ),
                    s3Client = FakeS3Client(),
                )
            u.saveBytes("k", "v".toByteArray())
            assertTrue(u.exists("k"))
        }
}
