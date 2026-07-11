package com.primandproper.platform.errors.http

import kotlin.test.Test
import kotlin.test.assertEquals
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

    // mirrors TestNewAPIErrorResponse
    @Test
    fun newApiErrorResponseStandard() {
        val details = ResponseDetails(currentAccountID = "account123", traceID = "trace456")
        val resp = newApiErrorResponse("something broke", ErrorCode.ErrTalkingToDatabase, details)

        assertEquals("something broke", resp.error.message)
        assertEquals(ErrorCode.ErrTalkingToDatabase, resp.error.code)
        assertEquals(details, resp.details)
    }

    @Test
    fun apiResponseSuccessCarriesData() {
        val resp = ApiResponse.Success("payload")
        assertEquals("payload", resp.data)
    }
}
