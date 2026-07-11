package com.primandproper.platform.encoding

/**
 * Configures the service's input/output encoding. Port of platform-go's `encoding.Config`: a single
 * [contentType] string, sourced from the `CONTENT_TYPE` environment variable / `contentType` JSON
 * field in Go.
 *
 * [validate] is the analog of Go's `ValidateWithContext`, which required the field to be non-empty
 * (`validation.Required`) — reported here by throwing [IllegalArgumentException] rather than returning
 * an error, exactly as `:httpclient`'s `HttpClientConfig.validate()`.
 */
public data class EncodingConfig(
    val contentType: String,
) {
    /** Validates the config, throwing [IllegalArgumentException] when [contentType] is blank. */
    public fun validate() {
        require(contentType.isNotBlank()) { "encoding: contentType is required" }
    }
}

/**
 * Resolves the configured [ContentType] from [config]. Port of Go's `ProvideContentType`, which runs
 * the raw string through `contentTypeFromString` — so an unrecognized value falls back to
 * [DEFAULT_CONTENT_TYPE] rather than failing.
 */
public fun contentType(config: EncodingConfig): ContentType = contentTypeFromMediaType(config.contentType)
