package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.errors.wrap
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream

/**
 * A [Bucket] decorator that prepends [prefix] to every path, the analog of gocloud.dev's
 * `blob.PrefixedBucket`. Applied by [newUploadManager] when [StorageConfig.bucketPrefix] is set, so
 * every object lives under a shared key prefix without the caller repeating it.
 */
public class PrefixedBucket(
    private val delegate: Bucket,
    private val prefix: String,
) : Bucket {
    override suspend fun write(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ): Long = delegate.write(prefix + path, source, options)

    override suspend fun newRangeReader(
        path: String,
        offset: Long,
        length: Long,
    ): RangeReaderResult = delegate.newRangeReader(prefix + path, offset, length)

    override suspend fun delete(path: String): Unit = delegate.delete(prefix + path)

    override suspend fun exists(path: String): Boolean = delegate.exists(prefix + path)

    override suspend fun attributes(path: String): Attributes = delegate.attributes(prefix + path)

    override fun list(prefix: String): Flow<ObjectInfo> =
        delegate.list(this.prefix + prefix).map { obj -> obj.copy(path = obj.path.removePrefix(this.prefix)) }

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String = delegate.signedUrl(prefix + path, options)
}

/**
 * Wraps [bucket] in a [PrefixedBucket] when [prefix] is non-empty, else returns it unchanged. Shared by
 * [newUploadManager] and the S3 backend so prefixing behaves identically across providers.
 */
public fun maybePrefixed(
    bucket: Bucket,
    prefix: String,
): Bucket = if (prefix.isEmpty()) bucket else PrefixedBucket(bucket, prefix)

/**
 * Builds an instrumented [Uploader] for the configured provider. Port of platform-go's
 * `objectstorage.NewUploadManager` + `selectBucket`, for the non-cloud providers this module owns:
 * [StorageProvider.MEMORY] and [StorageProvider.FILESYSTEM].
 *
 * The cloud providers dispatch elsewhere: [StorageProvider.S3] is served by `:uploads-s3`
 * (`newS3UploadManager`); [StorageProvider.GCP] / [StorageProvider.R2] / [StorageProvider.BACKBLAZE_B2]
 * are documented seams (`TODO(gcs)` / `TODO(r2)` / `TODO(b2)`). Selecting any of those here throws
 * [UnsupportedOperationException] pointing at the backend that owns it, rather than silently degrading.
 *
 * @throws StorageConfigException when [config] is invalid (mirrors Go wrapping the validation error).
 */
public fun newUploadManager(
    config: StorageConfig,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): Uploader {
    config.validate()

    val base: Bucket =
        when (config.resolvedProvider()) {
            StorageProvider.MEMORY -> MemoryBucket()
            StorageProvider.FILESYSTEM ->
                FilesystemBucket(
                    requireNotNull(config.filesystemConfig) { ErrNilConfig.message ?: "nil config provided" },
                )
            StorageProvider.S3 ->
                throw UnsupportedOperationException(
                    "s3 provider is served by :uploads-s3 — call newS3UploadManager",
                )
            StorageProvider.GCP ->
                throw UnsupportedOperationException("TODO(gcs): the GCS backend is not yet ported")
            StorageProvider.R2 ->
                throw UnsupportedOperationException("TODO(r2): the R2 backend is served by :uploads-s3 once wired")
            StorageProvider.BACKBLAZE_B2 ->
                throw UnsupportedOperationException("TODO(b2): the Backblaze B2 backend is served by :uploads-s3 once wired")
            null ->
                // Unreachable after validate(), but keeps the wrap-and-rethrow shape faithful to Go.
                throw (wrap(ErrUnknownProvider, config.provider) ?: ErrUnknownProvider)
        }

    return Uploader(maybePrefixed(base, config.bucketPrefix), config.bucketName, logger, tracerProvider)
}
