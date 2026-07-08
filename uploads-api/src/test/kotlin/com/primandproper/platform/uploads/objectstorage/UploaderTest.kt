package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.uploads.Attributer
import com.primandproper.platform.uploads.Lister
import com.primandproper.platform.uploads.RangeReader
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.UrlSigner
import com.primandproper.platform.uploads.listAll
import com.primandproper.platform.uploads.readBytes
import com.primandproper.platform.uploads.saveBytes
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the instrumented [Uploader] over both non-cloud backends ([MemoryBucket] and
 * [FilesystemBucket]) — the analog of platform-go's `objectstorage` file/uploader tests running against
 * gocloud's memblob/fileblob.
 */
class UploaderTest {
    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanup() {
        for (dir in tempDirs) dir.toFile().deleteRecursively()
    }

    private fun memoryUploader(observer: RecordingObserver? = null): Uploader =
        if (observer != null) Uploader(observer, MemoryBucket()) else Uploader(MemoryBucket(), "test")

    private fun filesystemUploader(): Uploader {
        val dir = Files.createTempDirectory("uploads-fs-test")
        tempDirs.add(dir)
        return Uploader(FilesystemBucket(FilesystemConfig(dir.toString())), "test")
    }

    private fun bothBackends(): List<Uploader> = listOf(memoryUploader(), filesystemUploader())

    @Test
    fun `save then read round-trips on both backends`() =
        runTest {
            for (u in bothBackends()) {
                u.saveBytes("dir/file.txt", "hello".toByteArray())
                assertContentEquals("hello".toByteArray(), u.readBytes("dir/file.txt"))
            }
        }

    @Test
    fun `exists reflects saves and deletes`() =
        runTest {
            for (u in bothBackends()) {
                assertFalse(u.exists("k"))
                u.saveBytes("k", "v".toByteArray())
                assertTrue(u.exists("k"))
                u.delete("k")
                assertFalse(u.exists("k"))
            }
        }

    @Test
    fun `attributes returns stored content type and size`() =
        runTest {
            for (u in bothBackends()) {
                u.saveBytes("k", "abcde".toByteArray(), SaveOptions(contentType = "text/plain", cacheControl = "max-age=60"))
                val attrs = (u as Attributer).attributes("k")
                assertEquals("text/plain", attrs.contentType)
                assertEquals("max-age=60", attrs.cacheControl)
                assertEquals(5, attrs.size)
            }
        }

    @Test
    fun `openRange reads a byte slice`() =
        runTest {
            for (u in bothBackends()) {
                u.saveBytes("k", "0123456789".toByteArray())
                val slice = (u as RangeReader).openRange("k", 2, 3).use { it.readBytes() }
                assertContentEquals("234".toByteArray(), slice)
            }
        }

    @Test
    fun `list yields objects under a prefix`() =
        runTest {
            for (u in bothBackends()) {
                u.saveBytes("a/1", "x".toByteArray())
                u.saveBytes("a/2", "y".toByteArray())
                u.saveBytes("b/1", "z".toByteArray())

                val underA = listAll(u as Lister, "a/").map { it.path }.toSet()
                assertEquals(setOf("a/1", "a/2"), underA)
            }
        }

    @Test
    fun `save records filename and length on the span`() =
        runTest {
            val obs = RecordingObserver()
            memoryUploader(obs).saveBytes("photo.png", "12345".toByteArray())

            val save = obs.operations.last { it.name == "Save" }
            assertEquals("photo.png", save.spanValues["filename"])
            assertEquals(5L, save.spanValues["length"])
            assertTrue(save.ended)
        }

    @Test
    fun `list records prefix and count on the span`() =
        runTest {
            val obs = RecordingObserver()
            val u = memoryUploader(obs)
            u.saveBytes("p/a", "x".toByteArray())
            u.saveBytes("p/b", "y".toByteArray())

            u.list("p/").toList()

            val listOp = obs.operations.last { it.name == "List" }
            assertEquals("p/", listOp.spanValues["prefix"])
            assertEquals(2, listOp.spanValues["object.count"])
            assertTrue(listOp.ended)
        }

    @Test
    fun `a read error is recorded on the span and rethrown`() =
        runTest {
            val obs = RecordingObserver()
            val u = memoryUploader(obs)

            assertFailsWith<NoSuchElementException> { u.open("absent") }

            val open = obs.operations.last { it.name == "Open" }
            assertTrue(open.errors.isNotEmpty())
            assertTrue(open.ended)
        }

    @Test
    fun `in-memory signing is unsupported`() =
        runTest {
            assertFailsWith<UnsupportedOperationException> { (memoryUploader() as UrlSigner).signedUrl("k") }
        }

    @Test
    fun `bucket prefix is applied transparently`() =
        runTest {
            val u = Uploader(maybePrefixed(MemoryBucket(), "tenant/"), "test")
            u.saveBytes("file", "v".toByteArray())
            // The caller path is prefix-free; listing returns prefix-free paths too.
            assertContentEquals("v".toByteArray(), u.readBytes("file"))
            assertEquals(listOf("file"), listAll(u as Lister, "").map { it.path })
        }

    @Test
    fun `newUploadManager builds a working memory backend`() =
        runTest {
            val u = newUploadManager(StorageConfig(bucketName = "b", provider = "memory"))
            u.saveBytes("k", "v".toByteArray())
            assertTrue(u.exists("k"))
        }

    @Test
    fun `newUploadManager rejects the s3 provider with a seam pointer`() {
        assertFailsWith<UnsupportedOperationException> {
            newUploadManager(StorageConfig(bucketName = "b", provider = "s3"))
        }
    }
}
