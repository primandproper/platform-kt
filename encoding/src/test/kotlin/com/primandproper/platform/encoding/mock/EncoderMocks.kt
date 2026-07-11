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
 *
 * Thread-safe: a per-mock lock guards the recording lists and every public accessor hands back an
 * immutable snapshot, so a recorder on one thread can't throw against a reader on another.
 */
public class ServerEncoderDecoderMock(
    public var encodeResponseWithStatusFunc: ((ResponseWriter, KSerializer<*>, Any?, Int) -> Unit)? = null,
    public var decodeRequestFunc: ((EncodedRequest, KSerializer<*>) -> Any?)? = null,
    public var decodeBytesFunc: ((ByteArray, KSerializer<*>) -> Any?)? = null,
    public var mustEncodeFunc: ((KSerializer<*>, Any?) -> ByteArray)? = null,
    public var mustEncodeJsonFunc: ((KSerializer<*>, Any?) -> ByteArray)? = null,
) : ServerEncoderDecoder {
    private val lock = Any()
    private val _encodeResponseWithStatusCalls = mutableListOf<EncodeResponseCall>()
    private val _decodeRequestCalls = mutableListOf<EncodedRequest>()
    private val _decodeBytesCalls = mutableListOf<ByteArray>()
    private val _mustEncodeCalls = mutableListOf<Any?>()
    private val _mustEncodeJsonCalls = mutableListOf<Any?>()

    public val encodeResponseWithStatusCalls: List<EncodeResponseCall>
        get() = synchronized(lock) { _encodeResponseWithStatusCalls.toList() }
    public val decodeRequestCalls: List<EncodedRequest>
        get() = synchronized(lock) { _decodeRequestCalls.toList() }
    public val decodeBytesCalls: List<ByteArray>
        get() = synchronized(lock) { _decodeBytesCalls.toList() }
    public val mustEncodeCalls: List<Any?>
        get() = synchronized(lock) { _mustEncodeCalls.toList() }
    public val mustEncodeJsonCalls: List<Any?>
        get() = synchronized(lock) { _mustEncodeJsonCalls.toList() }

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
        synchronized(lock) { _encodeResponseWithStatusCalls += EncodeResponseCall(response, value, statusCode) }
        requireFunc(encodeResponseWithStatusFunc, "encodeResponseWithStatusFunc").invoke(response, serializer, value, statusCode)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeRequest(
        request: EncodedRequest,
        serializer: KSerializer<T>,
    ): T {
        synchronized(lock) { _decodeRequestCalls += request }
        return requireFunc(decodeRequestFunc, "decodeRequestFunc").invoke(request, serializer) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeBytes(
        payload: ByteArray,
        serializer: KSerializer<T>,
    ): T {
        synchronized(lock) { _decodeBytesCalls += payload }
        return requireFunc(decodeBytesFunc, "decodeBytesFunc").invoke(payload, serializer) as T
    }

    override fun <T> mustEncode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        synchronized(lock) { _mustEncodeCalls += value }
        return requireFunc(mustEncodeFunc, "mustEncodeFunc").invoke(serializer, value)
    }

    override fun <T> mustEncodeJson(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        synchronized(lock) { _mustEncodeJsonCalls += value }
        return requireFunc(mustEncodeJsonFunc, "mustEncodeJsonFunc").invoke(serializer, value)
    }
}

/**
 * A configurable [ClientEncoder] test double, mirroring platform-go's moq-generated
 * `mockencoding.ClientEncoderMock`. Follows the same "null `Func` throws, calls are recorded" contract
 * as [ServerEncoderDecoderMock], including the same per-mock lock guarding its recording state.
 */
public class ClientEncoderMock(
    public var contentTypeFunc: (() -> String)? = null,
    public var unmarshalFunc: ((ByteArray, KSerializer<*>) -> Any?)? = null,
    public var encodeFunc: ((OutputStream, KSerializer<*>, Any?) -> Unit)? = null,
    public var encodeReaderFunc: ((KSerializer<*>, Any?) -> InputStream)? = null,
) : ClientEncoder {
    private val lock = Any()
    private var _contentTypeCalls = 0
    private val _unmarshalCalls = mutableListOf<ByteArray>()
    private val _encodeCalls = mutableListOf<Any?>()
    private val _encodeReaderCalls = mutableListOf<Any?>()

    /** Number of times [contentType] was read. */
    public val contentTypeCalls: Int
        get() = synchronized(lock) { _contentTypeCalls }
    public val unmarshalCalls: List<ByteArray>
        get() = synchronized(lock) { _unmarshalCalls.toList() }
    public val encodeCalls: List<Any?>
        get() = synchronized(lock) { _encodeCalls.toList() }
    public val encodeReaderCalls: List<Any?>
        get() = synchronized(lock) { _encodeReaderCalls.toList() }

    override val contentType: String
        get() {
            synchronized(lock) { _contentTypeCalls++ }
            return requireFunc(contentTypeFunc, "contentTypeFunc").invoke()
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unmarshal(
        data: ByteArray,
        serializer: KSerializer<T>,
    ): T {
        synchronized(lock) { _unmarshalCalls += data }
        return requireFunc(unmarshalFunc, "unmarshalFunc").invoke(data, serializer) as T
    }

    override fun <T> encode(
        destination: OutputStream,
        serializer: KSerializer<T>,
        value: T,
    ) {
        synchronized(lock) { _encodeCalls += value }
        requireFunc(encodeFunc, "encodeFunc").invoke(destination, serializer, value)
    }

    override fun <T> encodeReader(
        serializer: KSerializer<T>,
        value: T,
    ): InputStream {
        synchronized(lock) { _encodeReaderCalls += value }
        return requireFunc(encodeReaderFunc, "encodeReaderFunc").invoke(serializer, value)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
