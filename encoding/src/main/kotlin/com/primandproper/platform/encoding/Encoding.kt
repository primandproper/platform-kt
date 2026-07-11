package com.primandproper.platform.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.io.InputStream

/*
 * Package-level one-shot encode/decode helpers. Faithful port of platform-go's `encoding` utility
 * functions (`Decode`, `MustEncode`, `MustDecode`, `MustEncodeJSON`, `DecodeJSON`, `MustDecodeJSON`,
 * `MustJSONIntoReader`). Each spins up a noop-observability [ServerEncoderDecoder] / [ClientEncoder]
 * and delegates, so a caller with a value and a format needs no wiring.
 *
 * A `null` content type defaults to [ContentType.JSON], exactly as Go's `if ct == nil { ct = ...JSON }`.
 *
 * Go's `Must*` decode variants collapse into plain [decode] / [decodeJson] here: everything throws on
 * failure already in Kotlin, so the panic-vs-return distinction they encode does not exist. The
 * encoders keep no such distinction to shed either — they are simply named [encode] / [encodeJson] /
 * [jsonIntoReader].
 */

/** Decodes [data] into a `T`, defaulting to JSON when [contentType] is `null`. Port of `Decode`. */
public fun <T> decode(
    data: ByteArray,
    contentType: ContentType?,
    serializer: KSerializer<T>,
): T = ServerEncoderDecoder(contentType ?: ContentType.JSON).decodeBytes(data, serializer)

/** Encodes [value] to bytes, defaulting to JSON when [contentType] is `null`. Port of `MustEncode`. */
public fun <T> encode(
    value: T,
    contentType: ContentType?,
    serializer: KSerializer<T>,
): ByteArray {
    val out = ByteArrayOutputStream()
    ClientEncoder(contentType ?: ContentType.JSON).encode(out, serializer, value)
    return out.toByteArray()
}

/** JSON-encodes [value]. Port of `MustEncodeJSON`. */
public fun <T> encodeJson(
    value: T,
    serializer: KSerializer<T>,
): ByteArray = encode(value, ContentType.JSON, serializer)

/** JSON-decodes [data] into a `T`. Port of `DecodeJSON`. */
public fun <T> decodeJson(
    data: ByteArray,
    serializer: KSerializer<T>,
): T = decode(data, ContentType.JSON, serializer)

/** JSON-encodes [value] and returns a reader over the bytes. Port of `MustJSONIntoReader`. */
public fun <T> jsonIntoReader(
    value: T,
    serializer: KSerializer<T>,
): InputStream = encode(value, ContentType.JSON, serializer).inputStream()

// Reified conveniences for `@Serializable` types, so a caller can write `decode<Foo>(bytes, null)`.

/** [decode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> decode(
    data: ByteArray,
    contentType: ContentType? = null,
): T = decode(data, contentType, serializer())

/** [encode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> encode(
    value: T,
    contentType: ContentType? = null,
): ByteArray = encode(value, contentType, serializer())

/** [encodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> encodeJson(value: T): ByteArray = encodeJson(value, serializer())

/** [decodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> decodeJson(data: ByteArray): T = decodeJson(data, serializer())

/** [jsonIntoReader] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> jsonIntoReader(value: T): InputStream = jsonIntoReader(value, serializer())
