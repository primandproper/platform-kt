package com.primandproper.platform.encoding.mock

import com.primandproper.platform.encoding.ClientEncoder
import com.primandproper.platform.encoding.EncodedRequest
import com.primandproper.platform.encoding.ResponseWriter
import com.primandproper.platform.encoding.ServerEncoderDecoder
import kotlinx.serialization.KSerializer
import java.io.InputStream
import java.io.OutputStream

/**
 * A configurable [ServerEncoderDecoder] test double, mirroring platform-go's moq-generated
 * `mockencoding.ServerEncoderDecoderMock`. Each method delegates to a settable `...Func`; calling a
 * method whose `Func` was left `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call is recorded in the matching `...Calls`
 * list, standing in for moq's `XCalls()` accessors.
 *
 * The generic `KSerializer<T>` parameters are erased to `KSerializer<*>` in the `Func` types (a stored
 * lambda cannot itself be generic) — the direct analog of moq typing those arguments as Go's `any`.
 */
public class ServerEncoderDecoderMock(
    public var encodeResponseWithStatusFunc: ((ResponseWriter, KSerializer<*>, Any?, Int) -> Unit)? = null,
    public var decodeRequestFunc: ((EncodedRequest, KSerializer<*>) -> Any?)? = null,
    public var decodeBytesFunc: ((ByteArray, KSerializer<*>) -> Any?)? = null,
    public var mustEncodeFunc: ((KSerializer<*>, Any?) -> ByteArray)? = null,
    public var mustEncodeJsonFunc: ((KSerializer<*>, Any?) -> ByteArray)? = null,
) : ServerEncoderDecoder {
    public val encodeResponseWithStatusCalls: MutableList<EncodeResponseCall> = mutableListOf()
    public val decodeRequestCalls: MutableList<EncodedRequest> = mutableListOf()
    public val decodeBytesCalls: MutableList<ByteArray> = mutableListOf()
    public val mustEncodeCalls: MutableList<Any?> = mutableListOf()
    public val mustEncodeJsonCalls: MutableList<Any?> = mutableListOf()

    /** A recorded [ServerEncoderDecoder.encodeResponseWithStatus] call. */
    public data class EncodeResponseCall(
        val response: ResponseWriter,
        val value: Any?,
        val statusCode: Int,
    )

    override fun <T> encodeResponseWithStatus(
        response: ResponseWriter,
        serializer: KSerializer<T>,
        value: T,
        statusCode: Int,
    ) {
        encodeResponseWithStatusCalls += EncodeResponseCall(response, value, statusCode)
        requireFunc(encodeResponseWithStatusFunc, "encodeResponseWithStatusFunc").invoke(response, serializer, value, statusCode)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeRequest(
        request: EncodedRequest,
        serializer: KSerializer<T>,
    ): T {
        decodeRequestCalls += request
        return requireFunc(decodeRequestFunc, "decodeRequestFunc").invoke(request, serializer) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeBytes(
        payload: ByteArray,
        serializer: KSerializer<T>,
    ): T {
        decodeBytesCalls += payload
        return requireFunc(decodeBytesFunc, "decodeBytesFunc").invoke(payload, serializer) as T
    }

    override fun <T> mustEncode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        mustEncodeCalls += value
        return requireFunc(mustEncodeFunc, "mustEncodeFunc").invoke(serializer, value)
    }

    override fun <T> mustEncodeJson(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        mustEncodeJsonCalls += value
        return requireFunc(mustEncodeJsonFunc, "mustEncodeJsonFunc").invoke(serializer, value)
    }
}

/**
 * A configurable [ClientEncoder] test double, mirroring platform-go's moq-generated
 * `mockencoding.ClientEncoderMock`. Follows the same "null `Func` throws, calls are recorded" contract
 * as [ServerEncoderDecoderMock].
 */
public class ClientEncoderMock(
    public var contentTypeFunc: (() -> String)? = null,
    public var unmarshalFunc: ((ByteArray, KSerializer<*>) -> Any?)? = null,
    public var encodeFunc: ((OutputStream, KSerializer<*>, Any?) -> Unit)? = null,
    public var encodeReaderFunc: ((KSerializer<*>, Any?) -> InputStream)? = null,
) : ClientEncoder {
    public val contentTypeCalls: MutableList<Unit> = mutableListOf()
    public val unmarshalCalls: MutableList<ByteArray> = mutableListOf()
    public val encodeCalls: MutableList<Any?> = mutableListOf()
    public val encodeReaderCalls: MutableList<Any?> = mutableListOf()

    override val contentType: String
        get() {
            contentTypeCalls += Unit
            return requireFunc(contentTypeFunc, "contentTypeFunc").invoke()
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unmarshal(
        data: ByteArray,
        serializer: KSerializer<T>,
    ): T {
        unmarshalCalls += data
        return requireFunc(unmarshalFunc, "unmarshalFunc").invoke(data, serializer) as T
    }

    override fun <T> encode(
        destination: OutputStream,
        serializer: KSerializer<T>,
        value: T,
    ) {
        encodeCalls += value
        requireFunc(encodeFunc, "encodeFunc").invoke(destination, serializer, value)
    }

    override fun <T> encodeReader(
        serializer: KSerializer<T>,
        value: T,
    ): InputStream {
        encodeReaderCalls += value
        return requireFunc(encodeReaderFunc, "encodeReaderFunc").invoke(serializer, value)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
