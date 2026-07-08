package com.primandproper.platform.messagequeue

/**
 * Turns a to-be-published value into the bytes a queue transports. Port of the encode step platform-go
 * performs inside every publisher (`encoding.ClientEncoder.Encode(ctx, &b, data)` before the send).
 *
 * Go can JSON-encode any value by reflection; Kotlin has no universal reflective encoder, so — as with
 * `:cache-api`'s `CacheCodec` — serialization stays at the boundary and the encoder is injected.
 * [RawMessageEncoder] is the default; a caller wanting JSON (kotlinx.serialization), protobuf, or any
 * other format supplies its own [MessageEncoder].
 */
public fun interface MessageEncoder {
    /** Encodes [data] into the bytes to publish, throwing on an unencodable value. */
    public fun encode(data: Any): ByteArray
}

/**
 * The default [MessageEncoder]: a `ByteArray` is published verbatim and a `String` as its UTF-8 bytes.
 * Anything else is rejected with [IllegalArgumentException] rather than silently `toString`-ed, so a
 * caller publishing a structured value is pushed to supply a real serializing encoder — keeping
 * serialization an explicit boundary decision, as in platform-go.
 */
public object RawMessageEncoder : MessageEncoder {
    override fun encode(data: Any): ByteArray =
        when (data) {
            is ByteArray -> data
            is String -> data.encodeToByteArray()
            else -> throw IllegalArgumentException(
                "RawMessageEncoder encodes only ByteArray or String; supply a MessageEncoder for ${data::class.simpleName}",
            )
        }
}
