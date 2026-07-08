package com.primandproper.platform.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/** The shared round-trip fixture, the analog of the Go tests' `example` struct. */
@Serializable
internal data class Example(
    val name: String,
)

/**
 * A [KSerializer] that always fails, the Kotlin analog of Go's `broken` struct (a `json.Number` that
 * the marshaler rejects). Used to drive the encode-failure paths — `MustEncode` throwing, an
 * `encodeResponse` acknowledging the error, etc.
 */
internal object BrokenSerializer : KSerializer<Example> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Broken", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Example,
    ): Unit = throw SerializationException("intentionally broken serializer")

    override fun deserialize(decoder: Decoder): Example = throw SerializationException("intentionally broken serializer")
}

/** A [ResponseWriter] that records everything written to it — the analog of `httptest.NewRecorder`. */
internal class RecordingResponseWriter : ResponseWriter {
    val headers: MutableMap<String, String> = mutableMapOf()
    var statusCode: Int = 0
    val body: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()

    val bodyString: String get() = body.toString(Charsets.UTF_8)

    override fun setHeader(
        name: String,
        value: String,
    ) {
        headers[name] = value
    }

    override fun writeHeader(statusCode: Int) {
        this.statusCode = statusCode
    }

    override fun write(data: ByteArray) {
        body.write(data)
    }
}

/** An [OutputStream] whose [write] always fails, standing in for Go's write-error `io.Writer`. */
internal class ThrowingOutputStream : OutputStream() {
    override fun write(b: Int): Unit = throw java.io.IOException("write error")

    override fun write(b: ByteArray): Unit = throw java.io.IOException("write error")

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Unit = throw java.io.IOException("write error")
}

/** A simple [EncodedRequest] over in-memory bytes. */
internal class FakeRequest(
    override val contentType: String?,
    payload: ByteArray,
    private val stream: InputStream = ByteArrayInputStream(payload),
) : EncodedRequest {
    override fun body(): InputStream = stream
}

/** An [InputStream] over [data] whose [close] throws, to exercise the body-close-error log path. */
internal class CloseFailingStream(
    data: ByteArray,
) : InputStream() {
    private val delegate = ByteArrayInputStream(data)

    override fun read(): Int = delegate.read()

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = delegate.read(b, off, len)

    override fun close(): Unit = throw java.io.IOException("close error")
}
