package com.primandproper.platform.messagequeue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of platform-go's `TestErrEmptyTopicName`. */
class EmptyTopicNameExceptionTest {
    @Test
    fun `it is a throwable carrying the sentinel message`() {
        val error = EmptyTopicNameException()
        assertTrue(error is Throwable)
        assertEquals("empty topic name", error.message)
    }
}
