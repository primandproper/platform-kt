/*
 * files — ergonomic helpers for reading text files line by line or in fixed-size chunks, a port of
 * platform-go's `files` package (its reading core).
 *
 * `lines` and `chunks` are lazy Sequences over any java.io.Reader; the caller owns the Reader's
 * lifecycle. `sliceLines` reads no further than it needs to. Kotlin's stdlib already covers the
 * simplest cases (Reader.readLines / Reader.useLines / Path.useLines); the helpers here add
 * Go-faithful line splitting — only '\n' terminates a line, and a single '\r' immediately before it
 * is stripped, so a lone '\r' in the middle of a line is preserved rather than treated as a break —
 * plus fixed-size chunking and windowed slicing that report the sentinel errors in FilesException.
 */
package com.primandproper.platform.files

import java.io.BufferedReader
import java.io.Reader

/**
 * Yields each line of [reader] without its trailing newline, handling both `\n` and `\r\n`. An
 * unterminated final line is still yielded; a final empty line after a trailing newline is not. The
 * Sequence is single-pass and does not close [reader] — that stays the caller's responsibility.
 */
public fun lines(reader: Reader): Sequence<String> =
    sequence {
        val buffered = reader as? BufferedReader ?: reader.buffered()
        val builder = StringBuilder()
        while (true) {
            val read = buffered.read()
            if (read == -1) {
                if (builder.isNotEmpty()) yield(trimLineEnding(builder.toString()))
                break
            }

            val char = read.toChar()
            builder.append(char)
            if (char == '\n') {
                yield(trimLineEnding(builder.toString()))
                builder.setLength(0)
            }
        }
    }

/**
 * Yields successive lists of up to [n] lines of [reader]; the final list may hold fewer. [n] must be
 * greater than zero, otherwise [NonPositiveChunkSizeException] is thrown when this is called.
 */
public fun chunks(
    reader: Reader,
    n: Int,
): Sequence<List<String>> {
    if (n <= 0) throw NonPositiveChunkSizeException()
    return lines(reader).chunked(n)
}

/**
 * Returns up to [count] lines of [reader] after skipping [offset] lines — "the 10 lines after the
 * first 8" is `sliceLines(reader, 8, 10)`. It reads no further than needed. A negative [offset] or
 * [count] throws the matching sentinel; a zero [count] returns an empty list without reading; if
 * [offset] lands at or past the end of the input, [OffsetBeyondEofException] is thrown; if fewer
 * than [count] lines remain, the shorter list is returned.
 */
public fun sliceLines(
    reader: Reader,
    offset: Int,
    count: Int,
): List<String> {
    if (offset < 0) throw NegativeOffsetException()
    if (count < 0) throw NegativeCountException()
    if (count == 0) return emptyList()

    val out = ArrayList<String>(count)
    var skipped = 0
    var reached = false
    for (line in lines(reader)) {
        if (skipped < offset) {
            skipped++
            continue
        }

        reached = true
        out.add(line)
        if (out.size == count) break
    }

    if (!reached) throw OffsetBeyondEofException()
    return out
}

/**
 * Materializes every line of [reader]. A convenience for inputs small enough to hold in memory;
 * prefer [lines] for large inputs. An empty input yields an empty list.
 */
public fun allLines(reader: Reader): List<String> = lines(reader).toList()

/**
 * Materializes every chunk of up to [n] lines of [reader]. Like [allLines], for inputs small enough
 * to hold in memory. [n] must be greater than zero.
 */
public fun allChunks(
    reader: Reader,
    n: Int,
): List<List<String>> = chunks(reader, n).toList()

/** Strips a single trailing `\n` and an immediately preceding `\r`. */
private fun trimLineEnding(value: String): String = value.removeSuffix("\n").removeSuffix("\r")
