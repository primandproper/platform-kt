package com.primandproper.platform.encoding

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.spanBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializes and deserializes payloads for a service *client*. Faithful port of platform-go's
 * `encoding.ClientEncoder`.
 *
 * As with [ServerEncoderDecoder], each `any` becomes `T` + a `KSerializer<T>` (see the divergence
 * note in [ContentType]); the reified extensions below restore the terse call sites. Go's
 * `io.Writer`/`io.Reader` become [OutputStream]/[InputStream]. Every method opens an [Observer] span,
 * recording the payload length as Go does.
 */
public interface ClientEncoder {
    /** The configured content type's media-type string. Port of Go's `ContentType()`. */
    public val contentType: String

    /** Decodes [data] into a `T` using the configured content type. */
    public fun <T> unmarshal(
        data: ByteArray,
        serializer: KSerializer<T>,
    ): T

    /** Encodes [value] to [destination] using the configured content type. */
    public fun <T> encode(
        destination: OutputStream,
        serializer: KSerializer<T>,
        value: T,
    )

    /** Encodes [value] and returns a reader over the encoded bytes. Port of Go's `EncodeReader`. */
    public fun <T> encodeReader(
        serializer: KSerializer<T>,
        value: T,
    ): InputStream
}

internal class DefaultClientEncoder(
    private val format: ContentType,
    private val o11y: Observer,
) : ClientEncoder {
    override val contentType: String get() = format.mediaType

    override fun <T> unmarshal(
        data: ByteArray,
        serializer: KSerializer<T>,
    ): T =
        o11y.spanBlocking("Unmarshal") {
            // Record the length before decoding so it is observed even when the decode fails — the
            // property Go's `op.Set("data_length", len(data))` guarantees ahead of `unmarshalFunc`.
            set(DATA_LENGTH_KEY, data.size)
            val out = decodeFromBytes(format, serializer, data)
            logger.debug("unmarshalled")
            out
        }

    override fun <T> encode(
        destination: OutputStream,
        serializer: KSerializer<T>,
        value: T,
    ): Unit =
        o11y.spanBlocking("Encode") {
            // A serialization error or a downstream write error both surface as a thrown exception,
            // matching Go's `Encode` returning the marshaler/writer error.
            destination.write(encodeToBytes(format, serializer, value))
        }

    override fun <T> encodeReader(
        serializer: KSerializer<T>,
        value: T,
    ): InputStream =
        o11y.spanBlocking("EncodeReader") {
            val out = encodeToBytes(format, serializer, value)
            set(Keys.LENGTH, out.size)
            ByteArrayInputStream(out)
        }

    private companion object {
        // Go records the unmarshal length under the literal key "data_length".
        const val DATA_LENGTH_KEY = "data_length"
    }
}

/**
 * Builds a [ClientEncoder] for [contentType]. Constructor-style factory; [logger] and
 * [tracerProvider] default to noop.
 */
public fun ClientEncoder(
    contentType: ContentType,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): ClientEncoder = DefaultClientEncoder(contentType, Observer("client_encoder", logger, tracerProvider))

/** Go-named alias for [ClientEncoder]. Port of `ProvideClientEncoder`. */
public fun provideClientEncoder(
    logger: Logger?,
    tracerProvider: TracerProvider?,
    contentType: ContentType,
): ClientEncoder = ClientEncoder(contentType, logger, tracerProvider)

// Reified conveniences, mirroring the server-side ones.

/** [ClientEncoder.unmarshal] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ClientEncoder.unmarshal(data: ByteArray): T = unmarshal(data, serializer())

/** [ClientEncoder.encode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ClientEncoder.encode(
    destination: OutputStream,
    value: T,
): Unit = encode(destination, serializer(), value)

/** [ClientEncoder.encodeReader] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> ClientEncoder.encodeReader(value: T): InputStream = encodeReader(serializer(), value)
