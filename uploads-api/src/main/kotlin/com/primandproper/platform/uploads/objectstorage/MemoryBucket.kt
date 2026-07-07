package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

/**
 * An in-memory [Bucket], the analog of gocloud.dev's `memblob`. Objects live in a map keyed by path;
 * a [Mutex] guards it so concurrent writers are safe. A safe, dependency-free backend for tests and for
 * the [StorageProvider.MEMORY] provider.
 *
 * Content type is taken from [SaveOptions.contentType] when set, else sniffed from the bytes via
 * [java.net.URLConnection.guessContentTypeFromStream] — the modest analog of gocloud sniffing an unset
 * type on write. Signing is unsupported (memblob cannot sign), so [signedUrl] throws.
 */
public class MemoryBucket : Bucket {
    private class StoredObject(
        val data: ByteArray,
        val contentType: String?,
        val cacheControl: String?,
        val modTime: Instant,
    )

    private val mutex = Mutex()
    private val objects = LinkedHashMap<String, StoredObject>()

    override suspend fun write(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ): Long {
        val data = source.readBytes()
        val contentType = options.contentType ?: sniffContentType(data)
        mutex.withLock {
            objects[path] = StoredObject(data, contentType, options.cacheControl, Instant.now())
        }
        return data.size.toLong()
    }

    override suspend fun newRangeReader(
        path: String,
        offset: Long,
        length: Long,
    ): RangeReaderResult {
        val obj = mutex.withLock { objects[path] } ?: throw objectNotFound(path)
        val start = offset.coerceIn(0, obj.data.size.toLong()).toInt()
        val end = if (length < 0) obj.data.size else (start + length).coerceAtMost(obj.data.size.toLong()).toInt()
        val slice = obj.data.copyOfRange(start, end)
        return RangeReaderResult(ByteArrayInputStream(slice), slice.size.toLong())
    }

    override suspend fun delete(path: String) {
        mutex.withLock { objects.remove(path) }
    }

    override suspend fun exists(path: String): Boolean = mutex.withLock { objects.containsKey(path) }

    override suspend fun attributes(path: String): Attributes {
        val obj = mutex.withLock { objects[path] } ?: throw objectNotFound(path)
        return Attributes(
            contentType = obj.contentType,
            cacheControl = obj.cacheControl,
            etag = null,
            modTime = obj.modTime,
            size = obj.data.size.toLong(),
        )
    }

    override fun list(prefix: String): Flow<ObjectInfo> =
        flow {
            val snapshot = mutex.withLock { objects.toMap() }
            for ((path, obj) in snapshot) {
                if (path.startsWith(prefix)) {
                    emit(ObjectInfo(path = path, modTime = obj.modTime, size = obj.data.size.toLong(), isDir = false))
                }
            }
        }

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String = throw UnsupportedOperationException("signed URLs are not supported by the in-memory bucket")

    private fun objectNotFound(path: String): Throwable = NoSuchElementException("object not found: $path")

    private fun sniffContentType(data: ByteArray): String? = java.net.URLConnection.guessContentTypeFromStream(ByteArrayInputStream(data))
}
