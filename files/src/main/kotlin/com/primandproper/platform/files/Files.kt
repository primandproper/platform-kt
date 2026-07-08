package com.primandproper.platform.files

import java.nio.file.Path
import kotlin.io.path.bufferedReader

/**
 * Opens [path] and returns every one of its lines, closing the file before returning. The file-name
 * counterpart of [allLines]. A missing or unreadable [path] surfaces the underlying
 * `java.io.IOException` (e.g. `NoSuchFileException`); on Android pass an already-resolved,
 * readable path.
 */
public fun linesFile(path: Path): List<String> = path.bufferedReader().use { allLines(it) }

/**
 * Opens [path] and returns its lines grouped into chunks of up to [n], closing the file before
 * returning. [n] must be greater than zero, otherwise [NonPositiveChunkSizeException] is thrown
 * before the file is opened.
 */
public fun chunksFile(
    path: Path,
    n: Int,
): List<List<String>> {
    if (n <= 0) throw NonPositiveChunkSizeException()
    return path.bufferedReader().use { allChunks(it, n) }
}

/**
 * Opens [path] and returns up to [count] lines after skipping [offset] lines, closing the file
 * before returning. Validation and end-of-input behavior match [sliceLines].
 */
public fun sliceLinesFile(
    path: Path,
    offset: Int,
    count: Int,
): List<String> = path.bufferedReader().use { sliceLines(it, offset, count) }
