package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * The low-level, provider-specific storage surface — the analog of gocloud.dev's `blob.Bucket`. A
 * [Uploader] wraps one and owns all instrumentation (spans, and the `TODO(metrics)`/`TODO(circuitbreaking)`
 * seams), so each backend implements only the raw operations and stays free of observability wiring.
 *
 * This is the seam the S3 backend plugs into: `:uploads-s3` supplies an `S3Bucket` and reuses the same
 * [Uploader], exactly as platform-go instruments a single `Uploader` over whichever `blob.Bucket`
 * `selectBucket` opened. In-tree implementations are [MemoryBucket] and [FilesystemBucket].
 *
 * All methods suspend; backends that call blocking or future-returning clients bridge to coroutines
 * themselves (`Dispatchers.IO`, `CompletableFuture.await()`).
 */
public interface Bucket {
    /**
     * Writes [source] to the object at [path], returning the number of bytes written. A backend must
     * abort a partially-written object on failure — no truncated object may be committed at [path].
     */
    public suspend fun write(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ): Long

    /**
     * Opens a reader over [length] bytes of the object at [path] starting at [offset]; a negative
     * [length] reads to the end. The returned [RangeReaderResult] carries the readable [InputStream]
     * and the object/segment size.
     */
    public suspend fun newRangeReader(
        path: String,
        offset: Long,
        length: Long,
    ): RangeReaderResult

    /** Removes the object at [path]. */
    public suspend fun delete(path: String)

    /** Reports whether an object exists at [path]. */
    public suspend fun exists(path: String): Boolean

    /** Fetches the stored metadata for the object at [path]. */
    public suspend fun attributes(path: String): Attributes

    /** Streams the objects stored under [prefix] as a cold flow (see [com.primandproper.platform.uploads.Lister]). */
    public fun list(prefix: String): Flow<ObjectInfo>

    /**
     * Mints a signed URL for the object at [path]. Not every backend supports signing (in-memory and
     * unsigned filesystem do not) — those throw [UnsupportedOperationException], mirroring gocloud's
     * "signing not supported" error.
     */
    public suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String
}

/**
 * The result of [Bucket.newRangeReader]: the object [stream] (the caller's to close) and its [size] —
 * the analog of gocloud's `*blob.Reader`, whose `Size()` the Go `Uploader` records on the span.
 */
public class RangeReaderResult(
    public val stream: InputStream,
    public val size: Long,
)
