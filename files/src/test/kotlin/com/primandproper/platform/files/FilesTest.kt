package com.primandproper.platform.files

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilesTest {
    private fun withTempFile(
        contents: String,
        block: (Path) -> Unit,
    ) {
        val path = createTempFile(prefix = "files-test", suffix = ".txt")
        try {
            path.writeText(contents)
            block(path)
        } finally {
            path.deleteExisting()
        }
    }

    @Test
    fun linesFileReadsEveryLine() {
        withTempFile("first\nsecond\nthird\n") { path ->
            assertEquals(listOf("first", "second", "third"), linesFile(path))
        }
    }

    @Test
    fun linesFileYieldsUnterminatedFinalLine() {
        withTempFile("only-line") { path ->
            assertEquals(listOf("only-line"), linesFile(path))
        }
    }

    @Test
    fun chunksFileGroupsLines() {
        withTempFile("1\n2\n3\n4\n5\n") { path ->
            assertEquals(listOf(listOf("1", "2"), listOf("3", "4"), listOf("5")), chunksFile(path, 2))
        }
    }

    @Test
    fun chunksFileRejectsNonPositiveSize() {
        withTempFile("x\n") { path ->
            assertFailsWith<NonPositiveChunkSizeException> { chunksFile(path, 0) }
        }
    }

    @Test
    fun sliceLinesFileReturnsWindow() {
        withTempFile("a\nb\nc\nd\n") { path ->
            assertEquals(listOf("c", "d"), sliceLinesFile(path, 2, 5))
        }
    }

    @Test
    fun missingFileThrows() {
        val missing = Path.of("this-file-should-not-exist-${System.nanoTime()}.txt")
        assertFailsWith<IOException> { linesFile(missing) }
    }
}
