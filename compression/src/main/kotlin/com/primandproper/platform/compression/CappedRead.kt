package com.primandproper.platform.compression

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Drains [this] stream fully into a byte array, throwing [DecompressedTooLargeException] the moment
 * the running total would exceed [max]. Shared decompression-bomb guard for both backends: it plays
 * the role of Go's `WithDecoderMaxMemory` (zstd) and manual `io.CopyN(max+1)` (s2), applied
 * uniformly so a hostile payload fails fast instead of allocating its full expanded size.
 */
internal fun InputStream.readCapped(max: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > max) throw DecompressedTooLargeException()
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val DEFAULT_CHUNK_SIZE = 8 * 1024
