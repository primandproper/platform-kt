package com.primandproper.platform.errors

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorsTest {
    // --- sentinels (mirrors TestSentinelErrors) ---

    @Test
    fun errNilInputParameter() {
        assertNotNull(ErrNilInputParameter)
        assertContains(ErrNilInputParameter.message!!, "nil")
    }

    @Test
    fun errEmptyInputParameter() {
        assertNotNull(ErrEmptyInputParameter)
        assertContains(ErrEmptyInputParameter.message!!, "empty")
    }

    @Test
    fun errNilInputProvided() {
        assertNotNull(ErrNilInputProvided)
        assertContains(ErrNilInputProvided.message!!, "nil input")
    }

    @Test
    fun errInvalidIDProvided() {
        assertNotNull(ErrInvalidIDProvided)
        assertContains(ErrInvalidIDProvided.message!!, "ID")
    }

    @Test
    fun errEmptyInputProvided() {
        assertNotNull(ErrEmptyInputProvided)
        assertContains(ErrEmptyInputProvided.message!!, "empty")
    }

    @Test
    fun sentinelsAreDistinct() {
        assertFalse(isError(ErrNilInputParameter, ErrEmptyInputParameter))
        assertFalse(isError(ErrNilInputProvided, ErrInvalidIDProvided))
        assertFalse(isError(ErrEmptyInputProvided, ErrNilInputProvided))
    }

    // --- constructors (mirrors TestNew / TestNewf / TestErrorf) ---

    @Test
    fun newCreatesErrorWithMessage() {
        val err = newError("test error")
        assertEquals("test error", err.message)
    }

    @Test
    fun newfCreatesFormattedError() {
        val err = newErrorf("error %d: %s", 42, "details")
        assertContains(err.message!!, "42")
        assertContains(err.message!!, "details")
    }

    @Test
    fun errorfCreatesFormattedError() {
        val err = newErrorf("something %s", "failed")
        assertContains(err.message!!, "something failed")
    }

    // --- wrapping (mirrors TestWrap / TestWrapf) ---

    @Test
    fun wrapWrapsErrorWithMessage() {
        val inner = newError("inner")
        val wrapped = wrap(inner, "outer")
        assertNotNull(wrapped)
        assertTrue(isError(wrapped, inner))
        assertContains(wrapped.message!!, "outer")
    }

    @Test
    fun wrapNilReturnsNull() {
        assertNull(wrap(null, "outer"))
    }

    @Test
    fun wrapfWrapsErrorWithFormattedMessage() {
        val inner = newError("inner")
        val wrapped = wrapf(inner, "outer %d", 1)
        assertNotNull(wrapped)
        assertTrue(isError(wrapped, inner))
        assertContains(wrapped.message!!, "outer 1")
    }

    // --- Is/As analogs & Join (beyond the Go tests, exercising the new helpers) ---

    @Test
    fun asErrorFindsTypedCauseInChain() {
        val wrapped = wrap(ErrNilInputParameter, "context")
        assertNotNull(asError<PlatformException>(wrapped))
        assertTrue(isError(wrapped, ErrNilInputParameter))
    }

    @Test
    fun joinErrorsMatchesAnyMember() {
        val joined = joinErrors(ErrNilInputParameter, ErrEmptyInputParameter)
        assertNotNull(joined)
        assertTrue(isError(joined, ErrNilInputParameter))
        assertTrue(isError(joined, ErrEmptyInputParameter))
        assertFalse(isError(joined, ErrInvalidIDProvided))
    }

    @Test
    fun joinErrorsDropsNullsAndCollapses() {
        assertNull(joinErrors(null, null))
        assertEquals(ErrNilInputParameter, joinErrors(null, ErrNilInputParameter))
    }
}
