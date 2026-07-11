package com.primandproper.platform.messagequeue

/**
 * Turns a to-be-published value of type [T] into the bytes a queue transports. Port of the encode step
 * platform-go performs inside every publisher (`encoding.ClientEncoder.Encode(ctx, &b, data)` before
 * the send).
 *
 * Go can JSON-encode any value by reflection; Kotlin has no universal reflective encoder, so — as with
 * `:cache-api`'s `CacheCodec<T>` — serialization stays at the boundary and the encoder is injected.
 * The type parameter makes the publish path type-safe end to end ([Publisher] of the same [T]): a
 * mismatched value is rejected by the compiler rather than by a runtime type switch. [ByteArrayMessageEncoder]
 * is the default; a caller wanting JSON (kotlinx.serialization), protobuf, or any other format supplies
 * its own encoder ([StringMessageEncoder] is the ready-made UTF-8 one).
 */
public fun interface MessageEncoder<T : Any> {
    /** Encodes [data] into the bytes to publish, throwing on an unencodable value. */
    public fun encode(data: T): ByteArray
}

/**
 * The identity [MessageEncoder] for `ByteArray` payloads — publishes the bytes verbatim. The default
 * encoder for the raw-bytes publish path, mirroring `:cache-api`'s `StringCacheCodec` identity codec.
 */
public object ByteArrayMessageEncoder : MessageEncoder<ByteArray> {
    override fun encode(data: ByteArray): ByteArray = data
}

/** A [MessageEncoder] for `String` payloads — publishes the UTF-8 bytes. */
public object StringMessageEncoder : MessageEncoder<String> {
    override fun encode(data: String): ByteArray = data.encodeToByteArray()
}
