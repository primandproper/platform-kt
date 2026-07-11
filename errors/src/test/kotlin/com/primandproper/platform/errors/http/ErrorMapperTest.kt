package com.primandproper.platform.errors.http

import com.primandproper.platform.errors.CircuitBrokenException
import com.primandproper.platform.errors.EmptyInputParameterException
import com.primandproper.platform.errors.EmptyInputProvidedException
import com.primandproper.platform.errors.InvalidIDProvidedException
import com.primandproper.platform.errors.NoRowsException
import com.primandproper.platform.errors.UserAlreadyExistsException
import com.primandproper.platform.errors.isError
import com.primandproper.platform.errors.newError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorMapperTest {
    // A fresh registry per test method (JUnit instantiates the class per method), so a custom mapper
    // registered in one test never leaks into another — the isolation the process-global list lacked.
    private val registry = HttpErrorMapperRegistry()

    // --- PlatformHttpMapper (mirrors TestPlatformMapper_Map) ---

    @Test
    fun nilErrorReturnsNull() {
        assertNull(PlatformHttpMapper.map(null))
    }

    @Test
    fun errNoRowsMapsToDataNotFound() {
        val m = PlatformHttpMapper.map(NoRowsException())
        assertNotNull(m)
        assertEquals(ErrorCode.ErrDataNotFound, m.code)
        assertEquals("data not found", m.message)
    }

    @Test
    fun errUserAlreadyExistsMapsToValidatingRequestInput() {
        val m = PlatformHttpMapper.map(UserAlreadyExistsException())
        assertNotNull(m)
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("user already exists", m.message)
    }

    @Test
    fun errCircuitBrokenMapsToCircuitBroken() {
        val m = PlatformHttpMapper.map(CircuitBrokenException())
        assertNotNull(m)
        assertEquals(ErrorCode.ErrCircuitBroken, m.code)
        assertEquals("service temporarily unavailable", m.message)
    }

    @Test
    fun platformInputSentinelsMapToValidatingRequestInput() {
        for (err in listOf(
            EmptyInputParameterException(),
            InvalidIDProvidedException(),
            EmptyInputProvidedException(),
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
        val m = registry.toApiError(null)
        assertEquals(ErrorCode.ErrNothingSpecific, m.code)
        assertEquals("", m.message)
    }

    @Test
    fun toApiErrorKnownPlatformError() {
        val m = registry.toApiError(NoRowsException())
        assertEquals(ErrorCode.ErrDataNotFound, m.code)
        assertEquals("data not found", m.message)
    }

    @Test
    fun toApiErrorUnknownReturnsFallback() {
        val m = registry.toApiError(newError("totally unknown error that no mapper handles"))
        assertEquals(ErrorCode.ErrNothingSpecific, m.code)
        assertEquals("an error occurred", m.message)
    }

    @Test
    fun toApiErrorCircuitBroken() {
        val m = registry.toApiError(CircuitBrokenException())
        assertEquals(ErrorCode.ErrCircuitBroken, m.code)
        assertEquals("service temporarily unavailable", m.message)
    }

    @Test
    fun toApiErrorEmptyInputParameter() {
        val m = registry.toApiError(EmptyInputParameterException())
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("invalid input", m.message)
    }

    @Test
    fun toApiErrorUserAlreadyExists() {
        val m = registry.toApiError(UserAlreadyExistsException())
        assertEquals(ErrorCode.ErrValidatingRequestInput, m.code)
        assertEquals("user already exists", m.message)
    }

    // --- registerHttpErrorMapper (mirrors TestRegisterHTTPErrorMapper) ---

    @Test
    fun registersMapperConsultedByToApiError() {
        val customErr = newError("http-register-test-error")
        registry.register { err ->
            if (isError(err, customErr)) HttpMapping(ErrorCode("E_CUSTOM"), "custom message") else null
        }

        val m = registry.toApiError(customErr)
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
