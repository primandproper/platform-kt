package com.primandproper.platform.search.text.elasticsearch

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the path-segment encoding the production [LowLevelElasticsearchClient] applies to
 * index names and document ids before interpolating them into a REST path. A live cluster is not
 * required — the index-management behavior is covered in [ElasticsearchIndexManagerTest] against the
 * fake client; here we only pin the encoding that keeps a hostile id from escaping its path segment.
 */
class LowLevelElasticsearchClientTest {
    @Test
    fun `encodes an id containing a slash and a space`() {
        // A '/' would otherwise open extra path segments (hitting a different endpoint) and a raw
        // space is not a legal URI character at all (URIBuilder throws), so both must be escaped.
        assertEquals("a%2Fb%20c", encodeElasticsearchPathSegment("a/b c"))
    }

    @Test
    fun `encodes query and fragment delimiters that would otherwise inject into the request`() {
        // '?' and '#' would start a query string / fragment and silently change the request target.
        assertEquals("id%3Ffoo%3Dbar%23frag", encodeElasticsearchPathSegment("id?foo=bar#frag"))
    }

    @Test
    fun `encodes non-ascii bytes`() {
        assertEquals("caf%C3%A9", encodeElasticsearchPathSegment("café"))
    }

    @Test
    fun `leaves an ordinary id untouched`() {
        assertEquals("doc-123", encodeElasticsearchPathSegment("doc-123"))
    }
}
