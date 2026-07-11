package com.primandproper.platform.uploads.mock

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
import java.io.InputStream

/**
 * A configurable [UploadManager] test double, mirroring platform-go's moq-generated
 * `mockuploads.UploadManagerMock`. Each method delegates to a settable `...Func`; calling a method
 * whose `Func` was left `null` throws [IllegalStateException] — the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's arguments are recorded in the
 * matching `...Calls` list, standing in for moq's `XCalls()` accessors.
 *
 * Recording is guarded by a per-mock lock; the public accessors hand back an immutable snapshot, so a
 * recorder on one thread can't trip a reader iterating the calls on another.
 *
 * ```
 * val mock = UploadManagerMock(existsFunc = { path -> path == "known" })
 * ```
 */
public class UploadManagerMock(
    public var saveFunc: (suspend (String, InputStream, SaveOptions) -> Unit)? = null,
    public var openFunc: (suspend (String) -> InputStream)? = null,
    public var deleteFunc: (suspend (String) -> Unit)? = null,
    public var existsFunc: (suspend (String) -> Boolean)? = null,
) : UploadManager {
    private val lock = Any()

    private val _saveCalls = mutableListOf<Triple<String, InputStream, SaveOptions>>()
    private val _openCalls = mutableListOf<String>()
    private val _deleteCalls = mutableListOf<String>()
    private val _existsCalls = mutableListOf<String>()

    public val saveCalls: List<Triple<String, InputStream, SaveOptions>> get() = synchronized(lock) { _saveCalls.toList() }
    public val openCalls: List<String> get() = synchronized(lock) { _openCalls.toList() }
    public val deleteCalls: List<String> get() = synchronized(lock) { _deleteCalls.toList() }
    public val existsCalls: List<String> get() = synchronized(lock) { _existsCalls.toList() }

    override suspend fun save(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ) {
        synchronized(lock) { _saveCalls += Triple(path, source, options) }
        requireFunc(saveFunc, "saveFunc").invoke(path, source, options)
    }

    override suspend fun open(path: String): InputStream {
        synchronized(lock) { _openCalls += path }
        return requireFunc(openFunc, "openFunc").invoke(path)
    }

    override suspend fun delete(path: String) {
        synchronized(lock) { _deleteCalls += path }
        requireFunc(deleteFunc, "deleteFunc").invoke(path)
    }

    override suspend fun exists(path: String): Boolean {
        synchronized(lock) { _existsCalls += path }
        return requireFunc(existsFunc, "existsFunc").invoke(path)
    }
}

/** A configurable [RangeReader] double. Mirrors moq's `RangeReaderMock`. */
public class RangeReaderMock(
    public var openRangeFunc: (suspend (String, Long, Long) -> InputStream)? = null,
) : RangeReader {
    private val lock = Any()

    private val _openRangeCalls = mutableListOf<Triple<String, Long, Long>>()

    public val openRangeCalls: List<Triple<String, Long, Long>> get() = synchronized(lock) { _openRangeCalls.toList() }

    override suspend fun openRange(
        path: String,
        offset: Long,
        length: Long,
    ): InputStream {
        synchronized(lock) { _openRangeCalls += Triple(path, offset, length) }
        return requireFunc(openRangeFunc, "openRangeFunc").invoke(path, offset, length)
    }
}

/** A configurable [UrlSigner] double. Mirrors moq's `URLSignerMock`. */
public class UrlSignerMock(
    public var signedUrlFunc: (suspend (String, SignedUrlOptions) -> String)? = null,
) : UrlSigner {
    private val lock = Any()

    private val _signedUrlCalls = mutableListOf<Pair<String, SignedUrlOptions>>()

    public val signedUrlCalls: List<Pair<String, SignedUrlOptions>> get() = synchronized(lock) { _signedUrlCalls.toList() }

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String {
        synchronized(lock) { _signedUrlCalls += path to options }
        return requireFunc(signedUrlFunc, "signedUrlFunc").invoke(path, options)
    }
}

/** A configurable [Attributer] double. Mirrors moq's `AttributerMock`. */
public class AttributerMock(
    public var attributesFunc: (suspend (String) -> Attributes)? = null,
) : Attributer {
    private val lock = Any()

    private val _attributesCalls = mutableListOf<String>()

    public val attributesCalls: List<String> get() = synchronized(lock) { _attributesCalls.toList() }

    override suspend fun attributes(path: String): Attributes {
        synchronized(lock) { _attributesCalls += path }
        return requireFunc(attributesFunc, "attributesFunc").invoke(path)
    }
}

/** A configurable [Lister] double. Mirrors moq's `ListerMock`. */
public class ListerMock(
    public var listFunc: ((String) -> Flow<ObjectInfo>)? = null,
) : Lister {
    private val lock = Any()

    private val _listCalls = mutableListOf<String>()

    public val listCalls: List<String> get() = synchronized(lock) { _listCalls.toList() }

    override fun list(prefix: String): Flow<ObjectInfo> {
        synchronized(lock) { _listCalls += prefix }
        return requireFunc(listFunc, "listFunc").invoke(prefix)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
