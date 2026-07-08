package com.primandproper.platform.uploads.s3

import java.time.Instant

/**
 * The minimal S3 command surface [S3Bucket] needs — the analog of `:cache-redis`'s `RedisClient`. It
 * exists for the same reason: so the bucket mapping can be unit-tested against a fake without a live
 * bucket or AWS credentials. [AwsS3Client] is the production adapter over an `S3AsyncClient`.
 *
 * All methods suspend: the AWS adapter bridges each `CompletableFuture` to a coroutine via
 * `kotlinx.coroutines.future.await()`. Keys are already fully-qualified object keys — bucket selection
 * and any prefixing happen above this surface.
 */
public interface S3Client {
    /** Puts [data] at [key] with the optional stored `Content-Type` / `Cache-Control`. */
    public suspend fun putObject(
        key: String,
        data: ByteArray,
        contentType: String?,
        cacheControl: String?,
    )

    /**
     * Gets the object at [key]. [rangeHeader] is an HTTP `Range` value (`"bytes=0-9"`) or `null` for the
     * whole object. Throws when [key] is absent (the caller maps that to a not-found).
     */
    public suspend fun getObject(
        key: String,
        rangeHeader: String?,
    ): S3ObjectBytes

    /** Deletes the object at [key]. */
    public suspend fun deleteObject(key: String)

    /** Returns the object's metadata, or `null` when [key] is absent (S3's `NoSuchKey`). */
    public suspend fun headObject(key: String): S3ObjectMetadata?

    /**
     * Lists one page of objects under [prefix], continuing from [continuationToken] when non-null.
     * Recursive (no delimiter), matching gocloud's prefix listing.
     */
    public suspend fun listObjects(
        prefix: String,
        continuationToken: String?,
    ): S3ListPage
}

/** The bytes and length of a fetched object. */
public class S3ObjectBytes(
    public val data: ByteArray,
    public val contentLength: Long,
)

/** An object's stored metadata, as returned by a HEAD. */
public class S3ObjectMetadata(
    public val contentType: String?,
    public val cacheControl: String?,
    public val etag: String?,
    public val lastModified: Instant?,
    public val contentLength: Long,
)

/** A single entry in a listing page. */
public class S3ListEntry(
    public val key: String,
    public val size: Long,
    public val lastModified: Instant?,
)

/** One page of a listing: its [entries] and the [nextContinuationToken] (`null` when the last page). */
public class S3ListPage(
    public val entries: List<S3ListEntry>,
    public val nextContinuationToken: String?,
)
