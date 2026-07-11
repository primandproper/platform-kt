package com.primandproper.platform.errors

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorsTest {
    // --- error types (mirrors TestSentinelErrors) ---

    @Test
    fun emptyInputParameter() {
        assertContains(EmptyInputParameterException().message!!, "empty")
    }

    @Test
    fun invalidIDProvided() {
        assertContains(InvalidIDProvidedException().message!!, "ID")
    }

    @Test
    fun emptyInputProvided() {
        assertContains(EmptyInputProvidedException().message!!, "empty")
    }

    @Test
    fun typesAreDistinct() {
        // Each error type is matched by class, so a value of one type is never matched as another.
        assertTrue(isError<EmptyInputParameterException>(EmptyInputParameterException()))
        assertFalse(isError<EmptyInputParameterException>(InvalidIDProvidedException()))
        assertFalse(isError<EmptyInputProvidedException>(InvalidIDProvidedException()))
    }

    // --- constructors (mirrors TestNew / TestNewf / TestErrorf) ---

    @Test
    fun newCreatesErrorWithMessage() {
        val err = newError("test error")
        assertEquals("test error", err.message)
    }

    @Test
    fun newWithTemplateCreatesFormattedError() {
        val err = newError("error ${42}: ${"details"}")
        assertContains(err.message!!, "42")
        assertContains(err.message!!, "details")
    }

    // --- wrapping (mirrors TestWrap / TestWrapf) ---

    @Test
    fun wrapWrapsErrorWithMessage() {
        val inner = newError("inner")
        val wrapped = wrap(inner, "outer")
        assertTrue(isError(wrapped, inner))
        assertContains(wrapped.message!!, "outer")
    }

    @Test
    fun wrapWithTemplateWrapsErrorWithFormattedMessage() {
        val inner = newError("inner")
        val wrapped = wrap(inner, "outer ${1}")
        assertTrue(isError(wrapped, inner))
        assertContains(wrapped.message!!, "outer 1")
    }

    // --- Is/As analogs & Join (beyond the Go tests, exercising the new helpers) ---

    @Test
    fun asErrorFindsTypedCauseInChain() {
        val cause = EmptyInputParameterException()
        val wrapped = wrap(cause, "context")
        assertNotNull(asError<PlatformException>(wrapped))
        assertTrue(isError<EmptyInputParameterException>(wrapped))
        assertTrue(isError(wrapped, cause))
    }

    @Test
    fun joinErrorsMatchesAnyMember() {
        val joined = joinErrors(EmptyInputParameterException(), InvalidIDProvidedException())
        assertNotNull(joined)
        assertTrue(isError<EmptyInputParameterException>(joined))
        assertTrue(isError<InvalidIDProvidedException>(joined))
        assertFalse(isError<EmptyInputProvidedException>(joined))
    }

    @Test
    fun joinErrorsDropsNullsAndCollapses() {
        assertNull(joinErrors(null, null))
        val only = newError("only")
        assertEquals(only, joinErrors(null, only))
    }
}
