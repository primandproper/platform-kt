package com.primandproper.platform.files

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinesTest {
    private fun linesOf(text: String): List<String> = allLines(StringReader(text))

    @Test
    fun readsMultipleUnixLines() {
        assertEquals(listOf("alpha", "beta", "gamma"), linesOf("alpha\nbeta\ngamma\n"))
    }

    @Test
    fun handlesWindowsLineEndings() {
        assertEquals(listOf("alpha", "beta"), linesOf("alpha\r\nbeta\r\n"))
    }

    @Test
    fun yieldsUnterminatedFinalLine() {
        assertEquals(listOf("alpha", "beta"), linesOf("alpha\nbeta"))
    }

    @Test
    fun emptyInputYieldsNoLines() {
        assertTrue(linesOf("").isEmpty())
    }

    @Test
    fun preservesTrailingEmptyLineBetweenNewlines() {
        assertEquals(listOf("alpha", "", "beta"), linesOf("alpha\n\nbeta"))
    }

    @Test
    fun preservesLoneCarriageReturnInsideLine() {
        // Only '\n' terminates a line; a '\r' not immediately before it is part of the content.
        assertEquals(listOf("a\rb"), linesOf("a\rb\n"))
    }

    @Test
    fun chunksGroupsLinesAndKeepsShortFinalChunk() {
        val result = chunks(StringReader("1\n2\n3\n4\n5\n"), 2).toList()
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4"), listOf("5")), result)
    }

    @Test
    fun chunksRejectsNonPositiveSize() {
        assertFailsWith<NonPositiveChunkSizeException> { chunks(StringReader("x"), 0) }
        assertFailsWith<NonPositiveChunkSizeException> { allChunks(StringReader("x"), -1) }
    }

    @Test
    fun sliceLinesReturnsRequestedWindow() {
        val text = (1..10).joinToString("\n") { it.toString() }
        assertEquals(listOf("9", "10"), sliceLines(StringReader(text), 8, 10))
    }

    @Test
    fun sliceLinesReturnsShorterSliceWhenFewerRemain() {
        assertEquals(listOf("b", "c"), sliceLines(StringReader("a\nb\nc\n"), 1, 10))
    }

    @Test
    fun sliceLinesZeroCountIsEmpty() {
        assertTrue(sliceLines(StringReader("a\nb\n"), 0, 0).isEmpty())
    }

    @Test
    fun sliceLinesOffsetBeyondEofThrows() {
        assertFailsWith<OffsetBeyondEofException> { sliceLines(StringReader("a\nb\n"), 5, 2) }
    }

    @Test
    fun sliceLinesRejectsNegativeArguments() {
        assertFailsWith<NegativeOffsetException> { sliceLines(StringReader("a\n"), -1, 1) }
        assertFailsWith<NegativeCountException> { sliceLines(StringReader("a\n"), 0, -1) }
    }

    @Test
    fun allChunksMaterializesEveryChunk() {
        assertEquals(listOf(listOf("a", "b"), listOf("c")), allChunks(StringReader("a\nb\nc\n"), 2))
    }
}
