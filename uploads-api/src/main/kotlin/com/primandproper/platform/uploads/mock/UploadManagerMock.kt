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
    public val saveCalls: MutableList<Triple<String, InputStream, SaveOptions>> = mutableListOf()
    public val openCalls: MutableList<String> = mutableListOf()
    public val deleteCalls: MutableList<String> = mutableListOf()
    public val existsCalls: MutableList<String> = mutableListOf()

    override suspend fun save(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ) {
        saveCalls += Triple(path, source, options)
        requireFunc(saveFunc, "saveFunc").invoke(path, source, options)
    }

    override suspend fun open(path: String): InputStream {
        openCalls += path
        return requireFunc(openFunc, "openFunc").invoke(path)
    }

    override suspend fun delete(path: String) {
        deleteCalls += path
        requireFunc(deleteFunc, "deleteFunc").invoke(path)
    }

    override suspend fun exists(path: String): Boolean {
        existsCalls += path
        return requireFunc(existsFunc, "existsFunc").invoke(path)
    }
}

/** A configurable [RangeReader] double. Mirrors moq's `RangeReaderMock`. */
public class RangeReaderMock(
    public var openRangeFunc: (suspend (String, Long, Long) -> InputStream)? = null,
) : RangeReader {
    public val openRangeCalls: MutableList<Triple<String, Long, Long>> = mutableListOf()

    override suspend fun openRange(
        path: String,
        offset: Long,
        length: Long,
    ): InputStream {
        openRangeCalls += Triple(path, offset, length)
        return requireFunc(openRangeFunc, "openRangeFunc").invoke(path, offset, length)
    }
}

/** A configurable [UrlSigner] double. Mirrors moq's `URLSignerMock`. */
public class UrlSignerMock(
    public var signedUrlFunc: (suspend (String, SignedUrlOptions) -> String)? = null,
) : UrlSigner {
    public val signedUrlCalls: MutableList<Pair<String, SignedUrlOptions>> = mutableListOf()

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String {
        signedUrlCalls += path to options
        return requireFunc(signedUrlFunc, "signedUrlFunc").invoke(path, options)
    }
}

/** A configurable [Attributer] double. Mirrors moq's `AttributerMock`. */
public class AttributerMock(
    public var attributesFunc: (suspend (String) -> Attributes)? = null,
) : Attributer {
    public val attributesCalls: MutableList<String> = mutableListOf()

    override suspend fun attributes(path: String): Attributes {
        attributesCalls += path
        return requireFunc(attributesFunc, "attributesFunc").invoke(path)
    }
}

/** A configurable [Lister] double. Mirrors moq's `ListerMock`. */
public class ListerMock(
    public var listFunc: ((String) -> Flow<ObjectInfo>)? = null,
) : Lister {
    public val listCalls: MutableList<String> = mutableListOf()

    override fun list(prefix: String): Flow<ObjectInfo> {
        listCalls += prefix
        return requireFunc(listFunc, "listFunc").invoke(prefix)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
