package com.primandproper.platform.errors.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiResponseTest {
    // mirrors TestErrorResponse_Error
    @Test
    fun apiErrorErrorTextIsNonEmpty() {
        assertTrue(ApiError("", ErrorCode("")).errorText().isNotEmpty())
    }

    @Test
    fun apiErrorErrorTextFormat() {
        assertEquals("E104: boom", ApiError("boom", ErrorCode.ErrDataNotFound).errorText())
    }

    // mirrors TestAPIError_AsError
    @Test
    fun asErrorWithNullReceiver() {
        val e: ApiError? = null
        assertNull(e.asError())
    }

    @Test
    fun asErrorWithNonNullReceiver() {
        val e = ApiError("something went wrong", ErrorCode.ErrNothingSpecific)
        assertNotNull(e.asError())
    }

    // mirrors TestNewAPIErrorResponse
    @Test
    fun newApiErrorResponseStandard() {
        val details = ResponseDetails(currentAccountID = "account123", traceID = "trace456")
        val resp = newApiErrorResponse("something broke", ErrorCode.ErrTalkingToDatabase, details)

        assertNotNull(resp)
        assertNotNull(resp.error)
        assertEquals("something broke", resp.error!!.message)
        assertEquals(ErrorCode.ErrTalkingToDatabase, resp.error!!.code)
        assertEquals(details, resp.details)
    }
}
