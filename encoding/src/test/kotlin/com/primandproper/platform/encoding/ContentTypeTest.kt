package com.primandproper.platform.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of platform-go's `content_type_test.go`. */
class ContentTypeTest {
    @Test
    fun `media types are the application-slash wire values`() {
        assertEquals("application/json", ContentType.JSON.mediaType)
        assertEquals("application/xml", ContentType.XML.mediaType)
        assertEquals("application/toml", ContentType.TOML.mediaType)
        assertEquals("application/yaml", ContentType.YAML.mediaType)
        assertEquals("application/emoji", ContentType.EMOJI.mediaType)
    }

    @Test
    fun `contentTypeToMediaType returns the media type`() {
        assertEquals("application/json", contentTypeToMediaType(ContentType.JSON))
        assertTrue(contentTypeToMediaType(ContentType.XML).isNotEmpty())
        assertTrue(contentTypeToMediaType(ContentType.EMOJI).isNotEmpty())
    }

    @Test
    fun `contentTypeToMediaType of null is empty, mirroring ContentTypeToString(nil)`() {
        assertEquals("", contentTypeToMediaType(null))
    }

    @Test
    fun `contentTypeFromMediaType resolves each known media type`() {
        assertEquals(ContentType.JSON, contentTypeFromMediaType("application/json"))
        assertEquals(ContentType.XML, contentTypeFromMediaType("application/xml"))
        assertEquals(ContentType.TOML, contentTypeFromMediaType("application/toml"))
        assertEquals(ContentType.YAML, contentTypeFromMediaType("application/yaml"))
        assertEquals(ContentType.EMOJI, contentTypeFromMediaType("application/emoji"))
    }

    @Test
    fun `contentTypeFromMediaType defaults unknown to JSON`() {
        assertEquals(ContentType.JSON, contentTypeFromMediaType("unknown"))
    }

    @Test
    fun `contentTypeFromMediaType defaults null to JSON`() {
        assertEquals(ContentType.JSON, contentTypeFromMediaType(null))
    }

    @Test
    fun `contentTypeFromMediaType ignores charset parameter`() {
        assertEquals(ContentType.XML, contentTypeFromMediaType("application/xml; charset=utf-8"))
        assertEquals(ContentType.JSON, contentTypeFromMediaType("application/json; charset=utf-8"))
    }

    @Test
    fun `contentTypeFromMediaType trims and lowercases`() {
        assertEquals(ContentType.XML, contentTypeFromMediaType("  APPLICATION/XML  "))
    }

    @Test
    fun `CONTENT_TYPES lists every content type`() {
        assertEquals(5, CONTENT_TYPES.size)
        assertTrue(CONTENT_TYPES.containsAll(ContentType.entries))
    }

    @Test
    fun `default content type is JSON`() {
        assertEquals(ContentType.JSON, DEFAULT_CONTENT_TYPE)
    }

    @Test
    fun `content type header key matches the HTTP standard`() {
        assertEquals("Content-type", CONTENT_TYPE_HEADER_KEY)
    }
}
