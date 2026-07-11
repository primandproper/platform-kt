package com.primandproper.platform.encoding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Port of platform-go's `config_test.go` and `providers_test.go`. */
class EncodingConfigTest {
    @Test
    fun `validate accepts a populated content type`() {
        EncodingConfig(contentType = "application/json").validate()
    }

    @Test
    fun `validate rejects a blank content type`() {
        assertFailsWith<IllegalArgumentException> { EncodingConfig(contentType = "").validate() }
        assertFailsWith<IllegalArgumentException> { EncodingConfig(contentType = "   ").validate() }
    }

    @Test
    fun `contentType resolves the configured type`() {
        assertEquals(ContentType.JSON, contentType(EncodingConfig("application/json")))
        assertEquals(ContentType.XML, contentType(EncodingConfig("application/xml")))
    }

    @Test
    fun `contentType defaults an unknown type to JSON`() {
        assertEquals(ContentType.JSON, contentType(EncodingConfig("nonsense")))
    }
}
