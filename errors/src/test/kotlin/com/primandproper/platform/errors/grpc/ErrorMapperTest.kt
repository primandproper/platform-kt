package com.primandproper.platform.errors.grpc

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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorMapperTest {
    // A fresh registry per test method, so a mapper registered in one test never leaks into another.
    private val registry = GrpcErrorMapperRegistry()

    // --- PlatformGrpcMapper (mirrors TestPlatformMapper_Map) ---

    @Test
    fun nilErrorReturnsNull() {
        assertNull(PlatformGrpcMapper.map(null))
    }

    @Test
    fun errUserAlreadyExistsMapsToAlreadyExists() {
        assertEquals(GrpcCode.ALREADY_EXISTS, PlatformGrpcMapper.map(UserAlreadyExistsException()))
    }

    @Test
    fun errNoRowsMapsToNotFound() {
        assertEquals(GrpcCode.NOT_FOUND, PlatformGrpcMapper.map(NoRowsException()))
    }

    @Test
    fun errCircuitBrokenMapsToUnavailable() {
        assertEquals(GrpcCode.UNAVAILABLE, PlatformGrpcMapper.map(CircuitBrokenException()))
    }

    @Test
    fun platformInputSentinelsMapToInvalidArgument() {
        for (err in listOf(
            EmptyInputParameterException(),
            InvalidIDProvidedException(),
            EmptyInputProvidedException(),
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
        assertEquals(GrpcCode.OK, registry.mapToGrpc(null, GrpcCode.INTERNAL))
    }

    @Test
    fun knownPlatformErrorUsesPlatformMapper() {
        assertEquals(GrpcCode.NOT_FOUND, registry.mapToGrpc(NoRowsException(), GrpcCode.INTERNAL))
    }

    @Test
    fun unknownErrorReturnsDefault() {
        assertEquals(GrpcCode.ABORTED, registry.mapToGrpc(newError("truly unknown"), GrpcCode.ABORTED))
        assertNotEquals(GrpcCode.OK, registry.mapToGrpc(newError("truly unknown"), GrpcCode.ABORTED))
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
        registry.register { err ->
            if (isError(err, customErr)) GrpcCode.RESOURCE_EXHAUSTED else null
        }
        assertEquals(GrpcCode.RESOURCE_EXHAUSTED, registry.mapToGrpc(customErr, GrpcCode.INTERNAL))
    }

    // --- prepareGrpcStatus (mirrors TestPrepareAndLogGRPCStatus, minus observability) ---

    @Test
    fun prepareGrpcStatusReturnsCodeForKnownError() {
        val status = registry.prepareGrpcStatus(NoRowsException(), GrpcCode.INTERNAL, "fetching thing abc")
        assertNotNull(status)
        assertEquals(GrpcCode.NOT_FOUND, status.code)
    }

    @Test
    fun prepareGrpcStatusNilErrorReturnsNull() {
        assertNull(registry.prepareGrpcStatus(null, GrpcCode.INTERNAL, "something"))
    }

    @Test
    fun prepareGrpcStatusUnknownErrorUsesDefaultCode() {
        val status = registry.prepareGrpcStatus(newError("unknown"), GrpcCode.DATA_LOSS, "oops")
        assertNotNull(status)
        assertEquals(GrpcCode.DATA_LOSS, status.code)
    }
}
