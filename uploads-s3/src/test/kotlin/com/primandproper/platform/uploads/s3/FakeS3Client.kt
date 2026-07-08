package com.primandproper.platform.uploads.s3

import java.time.Instant

/**
 * An in-memory [S3Client] fake, standing in for a live bucket so [S3Bucket] mapping logic can be
 * unit-tested — the analog of `:cache-redis`'s `FakeRedisClient`. Honors the `Range` header, paginates
 * listings [pageSize] at a time so continuation-token handling is exercised, and can inject a failure on
 * a chosen operation.
 */
class FakeS3Client(
    private val pageSize: Int = 1000,
    var failOn: String? = null,
) : S3Client {
    private class Entry(
        val data: ByteArray,
        val contentType: String?,
        val cacheControl: String?,
        val lastModified: Instant,
    )

    private val store = linkedMapOf<String, Entry>()

    /** Every getObject invocation as (key, rangeHeader), so tests can assert a read was (not) issued. */
    val getObjectRequests = mutableListOf<Pair<String, String?>>()

    private fun maybeFail(op: String) {
        if (failOn == op) throw RuntimeException("injected $op failure")
    }

    override suspend fun putObject(
        key: String,
        data: ByteArray,
        contentType: String?,
        cacheControl: String?,
    ) {
        maybeFail("put")
        store[key] = Entry(data, contentType, cacheControl, Instant.now())
    }

    override suspend fun getObject(
        key: String,
        rangeHeader: String?,
    ): S3ObjectBytes {
        getObjectRequests += key to rangeHeader
        maybeFail("get")
        val entry = store[key] ?: throw NoSuchElementException("no such key: $key")
        val data = applyRange(entry.data, rangeHeader)
        return S3ObjectBytes(data, data.size.toLong())
    }

    override suspend fun deleteObject(key: String) {
        maybeFail("delete")
        store.remove(key)
    }

    override suspend fun headObject(key: String): S3ObjectMetadata? {
        maybeFail("head")
        val entry = store[key] ?: return null
        return S3ObjectMetadata(
            contentType = entry.contentType,
            cacheControl = entry.cacheControl,
            etag = "\"${entry.data.size}\"",
            lastModified = entry.lastModified,
            contentLength = entry.data.size.toLong(),
        )
    }

    override suspend fun listObjects(
        prefix: String,
        continuationToken: String?,
    ): S3ListPage {
        maybeFail("list")
        val matching = store.entries.filter { it.key.startsWith(prefix) }
        val start = continuationToken?.toInt() ?: 0
        val slice = matching.drop(start).take(pageSize)
        val entries = slice.map { S3ListEntry(it.key, it.value.data.size.toLong(), it.value.lastModified) }
        val nextIndex = start + slice.size
        val next = if (nextIndex < matching.size) nextIndex.toString() else null
        return S3ListPage(entries, next)
    }

    private fun applyRange(
        data: ByteArray,
        rangeHeader: String?,
    ): ByteArray {
        if (rangeHeader == null) return data
        val spec = rangeHeader.removePrefix("bytes=")
        val (startStr, endStr) = spec.split("-", limit = 2)
        val start = startStr.toInt()
        val end = if (endStr.isBlank()) data.size - 1 else endStr.toInt()
        return data.copyOfRange(start, (end + 1).coerceAtMost(data.size))
    }
}
