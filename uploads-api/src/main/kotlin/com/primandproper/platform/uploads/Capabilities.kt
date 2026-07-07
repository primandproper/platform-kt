package com.primandproper.platform.uploads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import java.io.InputStream
import java.time.Instant
import kotlin.time.Duration

/*
 * Optional capabilities. The core [UploadManager] only guarantees save/open/delete/exists; richer
 * backends also implement these. Callers that need one either accept the specific interface or check
 * it at runtime (`manager as? UrlSigner`). Port of platform-go's `uploads/capabilities.go`.
 */

/**
 * Opens a byte range of an object, for partial reads such as HTTP Range requests (video) or seeking
 * within columnar files (parquet). Port of Go's `uploads.RangeReader`.
 */
public interface RangeReader {
    /**
     * Returns a reader over [length] bytes of the object at [path], starting at [offset]. A negative
     * [length] reads to the end of the object. The caller must close the reader.
     */
    public suspend fun openRange(
        path: String,
        offset: Long,
        length: Long,
    ): InputStream
}

/**
 * Mints a signed URL granting temporary, direct access to an object, letting clients read or write
 * storage without proxying bytes through the service. Port of Go's `uploads.URLSigner`.
 */
public interface UrlSigner {
    public suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions = SignedUrlOptions(),
    ): String
}

/** Fetches an object's stored metadata. Port of Go's `uploads.Attributer`. */
public interface Attributer {
    public suspend fun attributes(path: String): Attributes
}

/**
 * Streams the objects stored under a prefix. Port of Go's `uploads.Lister`, whose
 * `iter.Seq2[ObjectInfo, error]` maps to a cold [Flow]: objects are fetched lazily as the flow is
 * collected, an error terminates collection by being thrown, and the caller stops early by cancelling
 * the collector (breaking out of `collect`).
 */
public interface Lister {
    public fun list(prefix: String): Flow<ObjectInfo>
}

/**
 * Drains a [Lister] into a list. Convenience for small listings; prefer collecting [Lister.list]
 * directly when a prefix may contain very many objects. Port of Go's `ListAll`.
 */
public suspend fun listAll(
    lister: Lister,
    prefix: String,
): List<ObjectInfo> = lister.list(prefix).toList()

/** The HTTP method a signed URL permits. Port of the string field on Go's `SignedURLOptions`. */
public enum class SignedUrlMethod {
    GET,
    PUT,
    DELETE,
}

/**
 * Configures a signed URL. Port of Go's `uploads.SignedURLOptions`.
 *
 * @param method the method the URL permits; defaults to [SignedUrlMethod.GET].
 * @param contentType for PUT URLs, the exact `Content-Type` the client must send.
 * @param expiry how long the URL is valid; [Duration.ZERO] means the provider default.
 */
public data class SignedUrlOptions(
    val method: SignedUrlMethod = SignedUrlMethod.GET,
    val contentType: String? = null,
    val expiry: Duration = Duration.ZERO,
)

/**
 * Describes a stored object. Port of Go's `uploads.Attributes`.
 *
 * @param modTime last-modified time, or `null` when the provider does not report one.
 */
public data class Attributes(
    val contentType: String? = null,
    val cacheControl: String? = null,
    val etag: String? = null,
    val modTime: Instant? = null,
    val size: Long = 0,
)

/**
 * Describes a single entry returned by [Lister.list]. Port of Go's `uploads.ObjectInfo`.
 *
 * @param modTime last-modified time, or `null` when the provider does not report one.
 */
public data class ObjectInfo(
    val path: String,
    val modTime: Instant? = null,
    val size: Long = 0,
    val isDir: Boolean = false,
)
