package com.primandproper.platform.errors.http

import com.primandproper.platform.errors.ErrCircuitBroken
import com.primandproper.platform.errors.ErrEmptyInputParameter
import com.primandproper.platform.errors.ErrEmptyInputProvided
import com.primandproper.platform.errors.ErrInvalidIDProvided
import com.primandproper.platform.errors.ErrNilInputParameter
import com.primandproper.platform.errors.ErrNilInputProvided
import com.primandproper.platform.errors.ErrNoRows
import com.primandproper.platform.errors.ErrUserAlreadyExists
import com.primandproper.platform.errors.isError
import com.primandproper.platform.errors.newError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorMapperTest {
    // --- PlatformHttpMapper (mirrors TestPlatformMapper_Map) ---

    @Test
    fun nilErrorReturnsNull() {
        assertNull(PlatformHttpMapper.map(null))
    }

    @Test
    fun errNoRowsMapsToDataNotFound() {
        val m = PlatformHttpMapper.map(ErrNoRows)
        assertNotNull(m)
        assertEquals(ErrorCode.ErrDataNotFound, m.code)
        assertEquals("data not found", m.message)
    }

    @Test
    fun errUserAlreadyExistsMapsToValidatingRequestInput() {
        val m = PlatformHttpMapper.map(ErrUserAlreadyExists)
        assertNotNull(m)
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("user already exists", m.message)
    }

    @Test
    fun errCircuitBrokenMapsToCircuitBroken() {
        val m = PlatformHttpMapper.map(ErrCircuitBroken)
        assertNotNull(m)
        assertEquals(ErrorCode.ErrCircuitBroken, m.code)
        assertEquals("service temporarily unavailable", m.message)
    }

    @Test
    fun platformInputSentinelsMapToValidatingRequestInput() {
        for (err in listOf(
            ErrNilInputParameter,
            ErrEmptyInputParameter,
            ErrNilInputProvided,
            ErrInvalidIDProvided,
            ErrEmptyInputProvided,
        )) {
            val m = PlatformHttpMapper.map(err)
            assertNotNull(m)
            assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        }
    }

    @Test
    fun unknownErrorReturnsNull() {
        assertNull(PlatformHttpMapper.map(newError("nope")))
    }

    // --- toApiError (mirrors TestToAPIError) ---

    @Test
    fun toApiErrorNil() {
        val m = toApiError(null)
        assertEquals(ErrorCode.ErrNothingSpecific, m.code)
        assertEquals("", m.message)
    }

    @Test
    fun toApiErrorKnownPlatformError() {
        val m = toApiError(ErrNoRows)
        assertEquals(ErrorCode.ErrDataNotFound, m.code)
        assertEquals("data not found", m.message)
    }

    @Test
    fun toApiErrorUnknownReturnsFallback() {
        val m = toApiError(newError("totally unknown error that no mapper handles"))
        assertEquals(ErrorCode.ErrNothingSpecific, m.code)
        assertEquals("an error occurred", m.message)
    }

    @Test
    fun toApiErrorCircuitBroken() {
        val m = toApiError(ErrCircuitBroken)
        assertEquals(ErrorCode.ErrCircuitBroken, m.code)
        assertEquals("service temporarily unavailable", m.message)
    }

    @Test
    fun toApiErrorNilInputParameter() {
        val m = toApiError(ErrNilInputParameter)
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("invalid input", m.message)
    }

    @Test
    fun toApiErrorUserAlreadyExists() {
        val m = toApiError(ErrUserAlreadyExists)
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("user already exists", m.message)
    }

    // --- registerHttpErrorMapper (mirrors TestRegisterHTTPErrorMapper) ---

    @Test
    fun registersMapperConsultedByToApiError() {
        val customErr = newError("http-register-test-error")
        registerHttpErrorMapper { err ->
            if (isError(err, customErr)) HttpMapping(ErrorCode("E_CUSTOM"), "custom message") else null
        }

        val m = toApiError(customErr)
        assertEquals(ErrorCode("E_CUSTOM"), m.code)
        assertEquals("custom message", m.message)
    }

    // --- toHttpStatus (new pure status mapping added in the port) ---

    @Test
    fun toHttpStatusCoversPlatformCodes() {
        assertEquals(404, ErrorCode.ErrDataNotFound.toHttpStatus())
        assertEquals(400, ErrorCode.ErrValidatingRequestInput.toHttpStatus())
        assertEquals(400, ErrorCode.ErrDecodingRequestInput.toHttpStatus())
        assertEquals(403, ErrorCode.ErrUserIsNotAuthorized.toHttpStatus())
        assertEquals(403, ErrorCode.ErrUserIsBanned.toHttpStatus())
        assertEquals(502, ErrorCode.ErrMisbehavingDependency.toHttpStatus())
        assertEquals(503, ErrorCode.ErrCircuitBroken.toHttpStatus())
        assertEquals(500, ErrorCode.ErrNothingSpecific.toHttpStatus())
        assertEquals(500, ErrorCode("E_CUSTOM").toHttpStatus())
    }
}
