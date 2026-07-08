package com.primandproper.platform.errors.grpc

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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorMapperTest {
    // --- PlatformGrpcMapper (mirrors TestPlatformMapper_Map) ---

    @Test
    fun nilErrorReturnsNull() {
        assertNull(PlatformGrpcMapper.map(null))
    }

    @Test
    fun errUserAlreadyExistsMapsToAlreadyExists() {
        assertEquals(GrpcCode.ALREADY_EXISTS, PlatformGrpcMapper.map(ErrUserAlreadyExists))
    }

    @Test
    fun errNoRowsMapsToNotFound() {
        assertEquals(GrpcCode.NOT_FOUND, PlatformGrpcMapper.map(ErrNoRows))
    }

    @Test
    fun errCircuitBrokenMapsToUnavailable() {
        assertEquals(GrpcCode.UNAVAILABLE, PlatformGrpcMapper.map(ErrCircuitBroken))
    }

    @Test
    fun platformInputSentinelsMapToInvalidArgument() {
        for (err in listOf(
            ErrNilInputParameter,
            ErrEmptyInputParameter,
            ErrNilInputProvided,
            ErrInvalidIDProvided,
            ErrEmptyInputProvided,
        )) {
            assertEquals(GrpcCode.INVALID_ARGUMENT, PlatformGrpcMapper.map(err))
        }
    }

    @Test
    fun unknownErrorReturnsNull() {
        assertNull(PlatformGrpcMapper.map(newError("nope")))
    }

    // --- mapToGrpc (mirrors TestMapToGRPC) ---

    @Test
    fun nilErrorReturnsOk() {
        assertEquals(GrpcCode.OK, mapToGrpc(null, GrpcCode.INTERNAL))
    }

    @Test
    fun knownPlatformErrorUsesPlatformMapper() {
        assertEquals(GrpcCode.NOT_FOUND, mapToGrpc(ErrNoRows, GrpcCode.INTERNAL))
    }

    @Test
    fun unknownErrorReturnsDefault() {
        assertEquals(GrpcCode.ABORTED, mapToGrpc(newError("truly unknown"), GrpcCode.ABORTED))
        assertNotEquals(GrpcCode.OK, mapToGrpc(newError("truly unknown"), GrpcCode.ABORTED))
    }

    @Test
    fun domainMapperConsultedWhenPlatformDoesNotMatch() {
        val customErr = newError("custom domain error")
        val mapper =
            GrpcErrorMapper { err ->
                if (isError(err, customErr)) GrpcCode.PERMISSION_DENIED else null
            }
        assertEquals(GrpcCode.PERMISSION_DENIED, mapper.map(customErr))
    }

    // --- registerGrpcErrorMapper (mirrors TestRegisterGRPCErrorMapper) ---

    @Test
    fun registersMapperFoundByMapToGrpc() {
        val customErr = newError("register-test-error")
        registerGrpcErrorMapper { err ->
            if (isError(err, customErr)) GrpcCode.RESOURCE_EXHAUSTED else null
        }
        assertEquals(GrpcCode.RESOURCE_EXHAUSTED, mapToGrpc(customErr, GrpcCode.INTERNAL))
    }

    // --- prepareGrpcStatus (mirrors TestPrepareAndLogGRPCStatus, minus observability) ---

    @Test
    fun prepareGrpcStatusReturnsCodeForKnownError() {
        val status = prepareGrpcStatus(ErrNoRows, GrpcCode.INTERNAL, "fetching thing %s", "abc")
        assertNotNull(status)
        assertEquals(GrpcCode.NOT_FOUND, status.code)
    }

    @Test
    fun prepareGrpcStatusNilErrorReturnsNull() {
        assertNull(prepareGrpcStatus(null, GrpcCode.INTERNAL, "something"))
    }

    @Test
    fun prepareGrpcStatusUnknownErrorUsesDefaultCode() {
        val status = prepareGrpcStatus(newError("unknown"), GrpcCode.DATA_LOSS, "oops")
        assertNotNull(status)
        assertEquals(GrpcCode.DATA_LOSS, status.code)
    }
}
