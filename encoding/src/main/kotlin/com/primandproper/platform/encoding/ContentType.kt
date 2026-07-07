package com.primandproper.platform.encoding

/*
 * # The Go(reflection) → Kotlin(kotlinx.serialization) divergence
 *
 * platform-go's `encoding` package leans on Go's reflection-driven marshalers: `encoding/json`,
 * `encoding/xml`, `gopkg.in/yaml.v3`, `BurntSushi/toml`, and `ecoji`+`gob` for emoji. Every method
 * takes an `any` and the marshaler walks the value's fields at runtime. There is no schema; the type
 * describes itself.
 *
 * Kotlin's idiomatic serializer, `kotlinx.serialization`, is the opposite: the compiler plugin
 * synthesizes a typed `KSerializer<T>` for every `@Serializable` class at build time, so serialization
 * is reflection-free and schema-checked. The cost is that the caller must hand the encoder a
 * `KSerializer<T>` (or a `@Serializable` type from which the reified helpers derive one) — which is
 * why the ported methods carry a `serializer: KSerializer<T>` parameter where Go carried a bare `any`.
 *
 * The consequences worth calling out:
 * - **JSON ships in-box** (`kotlinx-serialization-json`) and is a faithful, tested port.
 * - **XML / TOML / YAML / emoji** each need a *separate* kotlinx.serialization format library. Rather
 *   than vendor four more dependencies blind, they are documented `TODO(<fmt>)` seams in
 *   [EncodingFormat] that throw [UnsupportedContentFormatException] — the same descope
 *   `:cache`/`:circuitbreaking` make for their unported metrics pillar.
 * - **Strictness matches**: Go's `dec.DisallowUnknownFields()` is kotlinx.serialization's default
 *   (unknown keys throw), so the ported JSON reader rejects unknown fields exactly as Go does.
 * - **No trailing newline**: Go's streaming `json.Encoder.Encode` appends a `\n`; kotlinx.serialization
 *   does not. The ported bytes therefore omit the newline Go emits — noted where tests assert it.
 */

/** The HTTP standard header name for content type. Port of Go's `ContentTypeHeaderKey`. */
public const val CONTENT_TYPE_HEADER_KEY: String = "Content-type"

/**
 * A supported serialization format, selected per request/response. Port of platform-go's opaque
 * `ContentType` pointer type plus its `contentTypeJSON` / `contentTypeXML` / … string constants,
 * collapsed into the idiomatic Kotlin form: an enum whose [mediaType] is the wire value negotiated
 * against the `Content-type` header.
 *
 * Only [JSON] is a live format; [XML], [TOML], [YAML] and [EMOJI] are recognized for negotiation but
 * their encode/decode paths are documented seams (see [EncodingFormat]).
 */
public enum class ContentType(
    /** The `application/…` media type as it appears on the wire. */
    public val mediaType: String,
) {
    JSON("application/json"),
    XML("application/xml"),
    TOML("application/toml"),
    YAML("application/yaml"),
    EMOJI("application/emoji"),
}

/**
 * The format used when a header is absent or names nothing recognized. Mirrors Go's
 * `defaultContentType = ContentTypeJSON`.
 */
public val DEFAULT_CONTENT_TYPE: ContentType = ContentType.JSON

/**
 * Every content type, in declaration order. Port of Go's `ContentTypes` slice — handy for tests that
 * sweep all formats.
 */
public val CONTENT_TYPES: List<ContentType> = ContentType.entries

/**
 * The media type string for [contentType], or `""` when it is `null`. Faithful port of Go's
 * `ContentTypeToString`, including its `default: return ""` for a nil content type — a degenerate case
 * in Kotlin (the enum is never null) kept only so behavior maps one-to-one.
 */
public fun contentTypeToMediaType(contentType: ContentType?): String = contentType?.mediaType ?: ""

/**
 * Negotiates a [ContentType] from a raw `Content-type` header [value], falling back to
 * [DEFAULT_CONTENT_TYPE] for anything unrecognized. Port of Go's `contentTypeFromString`.
 *
 * Any media-type parameters (e.g. `application/xml; charset=utf-8`) are stripped so the match is on
 * the base media type — Go uses `mime.ParseMediaType`; here the equivalent is splitting on the first
 * `;`, then trimming and lowercasing.
 */
public fun contentTypeFromMediaType(value: String?): ContentType {
    if (value == null) return DEFAULT_CONTENT_TYPE
    val base = value.substringBefore(';').trim().lowercase()
    return CONTENT_TYPES.firstOrNull { it.mediaType == base } ?: DEFAULT_CONTENT_TYPE
}
