package com.primandproper.platform.uploads.noop

import com.primandproper.platform.uploads.Attributer
import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.Lister
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.RangeReader
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import com.primandproper.platform.uploads.UploadManager
import com.primandproper.platform.uploads.UrlSigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.InputStream

/**
 * A no-op [UploadManager] that also satisfies every optional capability — reads always return empty,
 * writes are discarded, existence is always `false`, and listing yields nothing. Port of platform-go's
 * `uploads/noop.UploadManager`. A safe default for wiring, and for tests that don't care about real
 * storage.
 */
public class NoopUploadManager :
    UploadManager,
    RangeReader,
    UrlSigner,
    Attributer,
    Lister {
    /** Drains [source] so a caller streaming into it still makes progress, mirroring Go's `io.Copy(io.Discard, r)`. */
    override suspend fun save(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ) {
        source.copyTo(NULL_OUTPUT_STREAM)
    }

    override suspend fun open(path: String): InputStream = InputStream.nullInputStream()

    override suspend fun openRange(
        path: String,
        offset: Long,
        length: Long,
    ): InputStream = InputStream.nullInputStream()

    override suspend fun delete(path: String) {
    }

    override suspend fun exists(path: String): Boolean = false

    override suspend fun attributes(path: String): Attributes = Attributes()

    override fun list(prefix: String): Flow<ObjectInfo> = emptyFlow()

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String = ""

    private companion object {
        private val NULL_OUTPUT_STREAM =
            object : java.io.OutputStream() {
                override fun write(b: Int) {}

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {}
            }
    }
}
