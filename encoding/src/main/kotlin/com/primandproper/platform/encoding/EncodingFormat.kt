package com.primandproper.platform.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Thrown when an encode/decode is requested for a [ContentType] whose format has not been ported.
 * These are the documented `TODO(<fmt>)` seams: Go marshals XML/TOML/YAML with `encoding/xml`,
 * `BurntSushi/toml` and `yaml.v3`, and emoji with `ecoji`+`gob`; each needs its own
 * kotlinx.serialization format library, which is left unvendored here (see the divergence note in
 * [ContentType]). JSON is the one live format.
 */
public class UnsupportedContentFormatException internal constructor(
    contentType: ContentType,
) : UnsupportedOperationException(
        "TODO(${contentType.name.lowercase()}): ${contentType.mediaType} encoding is not yet ported; " +
            "wire a kotlinx.serialization format for it (only ${ContentType.JSON.mediaType} is supported)",
    )

/**
 * The single shared JSON format. Configured to mirror Go's server/client encoders:
 * - `ignoreUnknownKeys = false` reproduces `dec.DisallowUnknownFields()` — an unexpected field is an
 *   error, not silently dropped.
 * - `encodeDefaults = true` matches `encoding/json`, which always writes every field (kotlinx.
 *   serialization otherwise omits values equal to their default).
 */
internal val PlatformJson: Json =
    Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

/**
 * Serializes [value] to bytes in [contentType]'s format via [serializer]. The central dispatch that
 * replaces Go's `switch e.contentType` over the reflection marshalers — only [ContentType.JSON] is
 * live; the rest raise [UnsupportedContentFormatException].
 */
internal fun <T> encodeToBytes(
    contentType: ContentType,
    serializer: KSerializer<T>,
    value: T,
): ByteArray =
    when (contentType) {
        ContentType.JSON -> PlatformJson.encodeToString(serializer, value).encodeToByteArray()
        // TODO(xml): port encoding/xml via a kotlinx.serialization XML format (e.g. xmlutil).
        // TODO(toml): port BurntSushi/toml via a kotlinx.serialization TOML format (e.g. ktoml).
        // TODO(yaml): port gopkg.in/yaml.v3 via a kotlinx.serialization YAML format (e.g. kaml).
        // TODO(emoji): port ecoji+gob; there is no kotlinx.serialization analog for gob framing.
        ContentType.XML, ContentType.TOML, ContentType.YAML, ContentType.EMOJI ->
            throw UnsupportedContentFormatException(contentType)
    }

/**
 * Deserializes [bytes] from [contentType]'s format into a `T` via [serializer]. The decode-side
 * counterpart of [encodeToBytes]; only [ContentType.JSON] is live.
 */
internal fun <T> decodeFromBytes(
    contentType: ContentType,
    serializer: KSerializer<T>,
    bytes: ByteArray,
): T =
    when (contentType) {
        ContentType.JSON -> PlatformJson.decodeFromString(serializer, bytes.decodeToString())
        // See the encode-side TODO(<fmt>) seams above.
        ContentType.XML, ContentType.TOML, ContentType.YAML, ContentType.EMOJI ->
            throw UnsupportedContentFormatException(contentType)
    }
