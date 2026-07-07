package com.primandproper.platform.files

import com.primandproper.platform.errors.PlatformException

/**
 * Base for the read-helper sentinels below, porting platform-go's `Err…` values. They extend
 * [PlatformException] so the platform's `isError`/`asError` cause-chain inspection sees them, while
 * remaining catchable by their concrete type. Their messages match the Go sentinels verbatim.
 */
public sealed class FilesException(
    message: String,
) : PlatformException(message)

/** Thrown when a chunk size is zero or negative. Ports `ErrNonPositiveChunkSize`. */
public class NonPositiveChunkSizeException : FilesException("chunk size must be greater than zero")

/** Thrown when a slice offset is negative. Ports `ErrNegativeOffset`. */
public class NegativeOffsetException : FilesException("offset must not be negative")

/** Thrown when a slice count is negative. Ports `ErrNegativeCount`. */
public class NegativeCountException : FilesException("count must not be negative")

/** Thrown when a slice offset lands at or past the end of the input. Ports `ErrOffsetBeyondEOF`. */
public class OffsetBeyondEofException : FilesException("offset is at or beyond end of input")
