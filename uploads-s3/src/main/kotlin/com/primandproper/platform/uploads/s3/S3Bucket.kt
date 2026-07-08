package com.primandproper.platform.uploads.s3

import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import com.primandproper.platform.uploads.objectstorage.Bucket
import com.primandproper.platform.uploads.objectstorage.RangeReaderResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * An S3-backed [Bucket], the analog of the `s3blob` bucket platform-go opens for the S3/R2/B2 providers.
 * It maps the storage-agnostic [Bucket] operations onto the narrow [S3Client] verb surface, so all AWS
 * SDK specifics stay in [AwsS3Client] and the mapping is unit-tested against a fake.
 *
 * The instrumented [com.primandproper.platform.uploads.objectstorage.Uploader] wraps this bucket and owns
 * the spans, so `S3Bucket` carries no observability of its own — matching how the in-tree [MemoryBucket]
 * /[FilesystemBucket] backends are plain.
 */
public class S3Bucket(
    private val client: S3Client,
) : Bucket {
    override suspend fun write(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ): Long {
        // AWS's async PutObject wants the full body up front; buffer the source, matching the
        // fully-buffered stance the platform-kt HTTP client and this port take elsewhere.
        val bytes = source.readBytes()
        client.putObject(path, bytes, options.contentType, options.cacheControl)
        return bytes.size.toLong()
    }

    override suspend fun newRangeReader(
        path: String,
        offset: Long,
        length: Long,
    ): RangeReaderResult {
        // A zero-length range is an empty read on the memory/filesystem backends; short-circuit so we
        // never emit an inverted `bytes=offset-(offset-1)` Range header (S3 answers 416 to it).
        if (length == 0L) return RangeReaderResult(ByteArrayInputStream(ByteArray(0)), 0L)
        val obj = client.getObject(path, rangeHeader(offset, length))
        return RangeReaderResult(ByteArrayInputStream(obj.data), obj.data.size.toLong())
    }

    override suspend fun delete(path: String): Unit = client.deleteObject(path)

    override suspend fun exists(path: String): Boolean = client.headObject(path) != null

    override suspend fun attributes(path: String): Attributes {
        val meta = client.headObject(path) ?: throw NoSuchElementException("object not found: $path")
        return Attributes(
            contentType = meta.contentType,
            cacheControl = meta.cacheControl,
            etag = meta.etag,
            modTime = meta.lastModified,
            size = meta.contentLength,
        )
    }

    override fun list(prefix: String): Flow<ObjectInfo> =
        flow {
            var token: String? = null
            do {
                val page = client.listObjects(prefix, token)
                for (entry in page.entries) {
                    emit(ObjectInfo(path = entry.key, modTime = entry.lastModified, size = entry.size, isDir = false))
                }
                token = page.nextContinuationToken
            } while (token != null)
        }

    /**
     * S3 presigned URLs require an `S3Presigner` (a separate, synchronous AWS component) rather than the
     * async client this backend drives. Left as a `TODO(signing)` seam; the Android signed-URL *client*
     * uploader (`:uploads-android`) consumes a URL minted elsewhere, so the read/write path here is
     * complete without it.
     */
    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String = throw UnsupportedOperationException("TODO(signing): S3 presigned URLs require an S3Presigner")

    /** Builds an HTTP `Range` header, or `null` for a full-object read (offset 0, unbounded length). */
    private fun rangeHeader(
        offset: Long,
        length: Long,
    ): String? =
        when {
            offset == 0L && length < 0L -> null
            length < 0L -> "bytes=$offset-"
            else -> "bytes=$offset-${offset + length - 1}"
        }
}
