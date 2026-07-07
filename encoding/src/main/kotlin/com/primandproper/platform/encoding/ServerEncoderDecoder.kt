package com.primandproper.platform.encoding

import com.primandproper.platform.errors.wrapf
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.spanBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.InputStream

/**
 * The minimal HTTP response sink a [ServerEncoderDecoder] writes into — the ported subset of Go's
 * `http.ResponseWriter` (header mutation, a status line, a body). Kept as a tiny interface, rather
 * than dragging in a server framework, so the encoder stays pure-JVM and a test can substitute a
 * recorder (the analog of Go's `httptest.NewRecorder`).
 */
public interface ResponseWriter {
    /** Sets response header [name] to [value], replacing any prior value. */
    public fun setHeader(
        name: String,
        value: String,
    )

    /** Writes the status line. Called once, before the body. */
    public fun writeHeader(statusCode: Int)

    /** Appends [data] to the response body. */
    public fun write(data: ByteArray)
}

/**
 * An inbound request whose body a [ServerEncoderDecoder] decodes — the ported subset of Go's
 * `*http.Request` the decoder touches: its `Content-type` header and its body stream.
 */
public interface EncodedRequest {
    /** The raw `Content-type` header value, or `null` when absent (negotiated to [DEFAULT_CONTENT_TYPE]). */
    public val contentType: String?

    /** Opens the request body. The decoder reads it fully and then closes it, logging a close failure. */
    public fun body(): InputStream
}

/**
 * Serializes response payloads and deserializes request payloads for the *server* side of a service.
 * Faithful port of platform-go's `encoding.ServerEncoderDecoder`.
 *
 * The `any` payloads of the Go interface become `T` + an explicit `KSerializer<T>` (see the
 * divergence note in [ContentType]); the reified `mustEncode<T>(...)` / `decodeBytes<T>(...)`
 * extensions below recover the ergonomics for `@Serializable` types.
 *
 * Every method opens an [Observer] span (via [spanBlocking] — encoding is synchronous CPU work, the
 * analog of Go's `ctx, op := o11y.Begin(ctx); defer op.End()`), recording the payload length / status
 * / content type as Go does with `op.Set(...)`.
 */
public interface ServerEncoderDecoder {
    /** Encodes [value] to [response] using the configured content type and writes [statusCode]. */
    public fun <T> encodeResponseWithStatus(
        response: ResponseWriter,
        serializer: KSerializer<T>,
        value: T,
        statusCode: Int,
    )

    /**
     * Decodes [request]'s body into a `T`, negotiating the format from the request's `Content-type`
     * header (not the server's configured type). Closes the body afterwards, logging a close failure.
     */
    public fun <T> decodeRequest(
        request: EncodedRequest,
        serializer: KSerializer<T>,
    ): T

    /** Decodes [payload] into a `T` using the server's *configured* content type. */
    public fun <T> decodeBytes(
        payload: ByteArray,
        serializer: KSerializer<T>,
    ): T

    /** Encodes [value] to bytes using the configured content type, throwing on failure. */
    public fun <T> mustEncode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray

    /** Encodes [value] to JSON bytes regardless of the configured content type, throwing on failure. */
    public fun <T> mustEncodeJson(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray
}

internal class DefaultServerEncoderDecoder(
    private val contentType: ContentType,
    private val o11y: Observer,
) : ServerEncoderDecoder {
    override fun <T> encodeResponseWithStatus(
        response: ResponseWriter,
        serializer: KSerializer<T>,
        value: T,
        statusCode: Int,
    ): Unit =
        o11y.spanBlocking("EncodeResponse") {
            set(Keys.RESPONSE_STATUS, statusCode)

            // Choose the encoder from the configured content type, not a pre-set header, so a
            // configured encoder is honored even when the handler never sets a header — and set the
            // header ourselves. Faithful to Go's `encodeResponse` comment.
            response.setHeader(CONTENT_TYPE_HEADER_KEY, contentType.mediaType)
            response.writeHeader(statusCode)

            // Encode to bytes first, then write; a serialization failure therefore leaves the body
            // empty rather than half-written. Go acknowledges the error on the operation and moves on
            // (the response is already committed), so we do the same instead of rethrowing.
            try {
                response.write(encodeToBytes(contentType, serializer, value))
            } catch (e: Exception) {
                acknowledge(e, "encoding response")
            }
        }

    override fun <T> decodeRequest(
        request: EncodedRequest,
        serializer: KSerializer<T>,
    ): T =
        o11y.spanBlocking("DecodeRequest") {
            val negotiated = contentTypeFromMediaType(request.contentType)
            val stream = request.body()
            try {
                decodeFromBytes(negotiated, serializer, stream.readBytes())
            } finally {
                // Go defers `req.Body.Close()` and only logs a close error; the decode result stands.
                try {
                    stream.close()
                } catch (e: Exception) {
                    logger.error("closing request body", e)
                }
            }
        }

    override fun <T> decodeBytes(
        payload: ByteArray,
        serializer: KSerializer<T>,
    ): T =
        o11y.spanBlocking("DecodeBytes") {
            set(Keys.LENGTH, payload.size)
            set(CONTENT_TYPE_KEY, contentType.mediaType)
            decodeFromBytes(contentType, serializer, payload)
        }

    override fun <T> mustEncode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray =
        o11y.spanBlocking("MustEncode") {
            try {
                encodeToBytes(contentType, serializer, value)
            } catch (e: Exception) {
                throw wrapf(e, "encoding %s content", contentType.mediaType) ?: e
            }
        }

    override fun <T> mustEncodeJson(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray =
        o11y.spanBlocking("MustEncodeJSON") {
            try {
                encodeToBytes(ContentType.JSON, serializer, value)
            } catch (e: Exception) {
                throw wrapf(e, "encoding JSON content") ?: e
            }
        }

    private companion object {
        // Go records this under the literal key "content_type" (not one of the standard keys).
        const val CONTENT_TYPE_KEY = "content_type"
    }
}

/**
 * Builds a [ServerEncoderDecoder] for [contentType]. Constructor-style factory; [logger] and
 * [tracerProvider] default to noop, matching how the package-level helpers construct one from noop
 * observability.
 */
public fun ServerEncoderDecoder(
    contentType: ContentType,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): ServerEncoderDecoder = DefaultServerEncoderDecoder(contentType, Observer("server_encoder_decoder", logger, tracerProvider))

/** Go-named alias for [ServerEncoderDecoder]. Port of `ProvideServerEncoderDecoder`. */
public fun provideServerEncoderDecoder(
    logger: Logger?,
    tracerProvider: TracerProvider?,
    contentType: ContentType,
): ServerEncoderDecoder = ServerEncoderDecoder(contentType, logger, tracerProvider)

/**
 * Encodes [value] to [response] with HTTP 200. Port of Go's concrete `RespondWithData`, kept as an
 * extension because it is a convenience over [ServerEncoderDecoder.encodeResponseWithStatus] rather
 * than part of the core interface (as in Go).
 */
public fun <T> ServerEncoderDecoder.respondWithData(
    response: ResponseWriter,
    serializer: KSerializer<T>,
    value: T,
): Unit = encodeResponseWithStatus(response, serializer, value, HTTP_STATUS_OK)

/** HTTP 200, the status [respondWithData] writes. Mirrors Go's `http.StatusOK`. */
public const val HTTP_STATUS_OK: Int = 200

// Reified conveniences: recover Go's `any` ergonomics for `@Serializable` types by deriving the
// serializer at the call site, so callers write `ed.mustEncode(value)` rather than threading one.

/** [ServerEncoderDecoder.mustEncode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.mustEncode(value: T): ByteArray = mustEncode(serializer(), value)

/** [ServerEncoderDecoder.mustEncodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.mustEncodeJson(value: T): ByteArray = mustEncodeJson(serializer(), value)

/** [ServerEncoderDecoder.decodeBytes] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.decodeBytes(payload: ByteArray): T = decodeBytes(payload, serializer())

/** [ServerEncoderDecoder.decodeRequest] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.decodeRequest(request: EncodedRequest): T = decodeRequest(request, serializer())

/** [ServerEncoderDecoder.encodeResponseWithStatus] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.encodeResponseWithStatus(
    response: ResponseWriter,
    value: T,
    statusCode: Int,
): Unit = encodeResponseWithStatus(response, serializer(), value, statusCode)

/** [respondWithData] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ServerEncoderDecoder.respondWithData(
    response: ResponseWriter,
    value: T,
): Unit = respondWithData(response, serializer(), value)
