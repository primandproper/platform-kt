package com.primandproper.platform.uploads

import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Reads and writes objects in a storage provider. Port of platform-go's `uploads.UploadManager`.
 *
 * Coroutine-native: Go threads a `context.Context` through each method; the Kotlin port suspends
 * instead. Bytes cross the boundary as [InputStream] — the analog of Go's `io.Reader`/`io.ReadCloser`
 * — so a caller streams rather than buffering a whole object into memory. The reader [open] returns is
 * the caller's to close.
 *
 * The core contract only guarantees save/open/delete/exists; richer backends (see
 * [com.primandproper.platform.uploads.objectstorage.Uploader]) also implement the optional capability
 * interfaces in `Capabilities.kt`, which callers reach via a runtime check (`manager as? UrlSigner`).
 */
public interface UploadManager {
    /** Writes the contents of [source] to the object at [path]. */
    public suspend fun save(
        path: String,
        source: InputStream,
        options: SaveOptions = SaveOptions(),
    )

    /** Returns a reader for the object at [path]. The caller must close it. */
    public suspend fun open(path: String): InputStream

    /** Removes the object at [path]. */
    public suspend fun delete(path: String)

    /** Reports whether an object exists at [path]. */
    public suspend fun exists(path: String): Boolean
}

/**
 * The resolved settings for a [UploadManager.save] call. Port of platform-go's `uploads.SaveOptions`
 * (whose functional `SaveOption`/`WithContentType`/`WithCacheControl` options collapse to named,
 * defaulted parameters here — the idiomatic Kotlin form of the same "optional, additive settings").
 *
 * @param contentType the stored `Content-Type`; when `null` the provider sniffs it from the content on
 *   write.
 * @param cacheControl the stored `Cache-Control` header for served objects.
 */
public data class SaveOptions(
    val contentType: String? = null,
    val cacheControl: String? = null,
)

/** Saves [content] via [UploadManager.save]. Convenience for a byte slice; port of Go's `SaveFile`. */
public suspend fun UploadManager.saveBytes(
    path: String,
    content: ByteArray,
    options: SaveOptions = SaveOptions(),
): Unit = save(path, ByteArrayInputStream(content), options)

/**
 * Reads an entire object into memory via [UploadManager.open], closing the reader afterwards. Port of
 * Go's `ReadFile`.
 */
public suspend fun UploadManager.readBytes(path: String): ByteArray = open(path).use { it.readBytes() }
