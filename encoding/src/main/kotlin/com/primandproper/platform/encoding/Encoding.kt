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
 * The `Must*` variants throw on failure where Go panics — the JVM idiom for "this must not fail".
 */

/** Decodes [data] into a `T`, defaulting to JSON when [contentType] is `null`. Port of `Decode`. */
public fun <T> decode(
    data: ByteArray,
    contentType: ContentType?,
    serializer: KSerializer<T>,
): T = ServerEncoderDecoder(contentType ?: ContentType.JSON).decodeBytes(data, serializer)

/** Encodes [value] to bytes, defaulting to JSON when [contentType] is `null`. Port of `MustEncode`. */
public fun <T> mustEncode(
    value: T,
    contentType: ContentType?,
    serializer: KSerializer<T>,
): ByteArray {
    val out = ByteArrayOutputStream()
    ClientEncoder(contentType ?: ContentType.JSON).encode(out, serializer, value)
    return out.toByteArray()
}

/** [decode] that throws on failure instead of returning it. Port of `MustDecode`. */
public fun <T> mustDecode(
    data: ByteArray,
    contentType: ContentType?,
    serializer: KSerializer<T>,
): T = decode(data, contentType, serializer)

/** JSON-encodes [value]. Port of `MustEncodeJSON`. */
public fun <T> mustEncodeJson(
    value: T,
    serializer: KSerializer<T>,
): ByteArray = mustEncode(value, ContentType.JSON, serializer)

/** JSON-decodes [data] into a `T`. Port of `DecodeJSON`. */
public fun <T> decodeJson(
    data: ByteArray,
    serializer: KSerializer<T>,
): T = decode(data, ContentType.JSON, serializer)

/** JSON-decodes [data], throwing on failure. Port of `MustDecodeJSON`. */
public fun <T> mustDecodeJson(
    data: ByteArray,
    serializer: KSerializer<T>,
): T = mustDecode(data, ContentType.JSON, serializer)

/** JSON-encodes [value] and returns a reader over the bytes. Port of `MustJSONIntoReader`. */
public fun <T> mustJsonIntoReader(
    value: T,
    serializer: KSerializer<T>,
): InputStream = mustEncode(value, ContentType.JSON, serializer).inputStream()

// Reified conveniences for `@Serializable` types, so a caller can write `decode<Foo>(bytes, null)`.

/** [decode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> decode(
    data: ByteArray,
    contentType: ContentType? = null,
): T = decode(data, contentType, serializer())

/** [mustEncode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> mustEncode(
    value: T,
    contentType: ContentType? = null,
): ByteArray = mustEncode(value, contentType, serializer())

/** [mustDecode] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> mustDecode(
    data: ByteArray,
    contentType: ContentType? = null,
): T = mustDecode(data, contentType, serializer())

/** [mustEncodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> mustEncodeJson(value: T): ByteArray = mustEncodeJson(value, serializer())

/** [decodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> decodeJson(data: ByteArray): T = decodeJson(data, serializer())

/** [mustDecodeJson] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> mustDecodeJson(data: ByteArray): T = mustDecodeJson(data, serializer())

/** [mustJsonIntoReader] deriving the serializer for a `@Serializable` [T]. */
public inline fun <reified T> mustJsonIntoReader(value: T): InputStream = mustJsonIntoReader(value, serializer())
