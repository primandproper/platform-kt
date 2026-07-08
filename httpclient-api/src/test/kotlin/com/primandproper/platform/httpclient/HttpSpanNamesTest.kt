package com.primandproper.platform.httpclient

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSpanNamesTest {
    @Test
    fun `numeric path segments collapse to id`() {
        assertEquals("GET /users/<id>/posts/<id>", HttpSpanNames.format(HttpMethod.GET, "/users/42/posts/7"))
    }

    @Test
    fun `paths without ids are left alone`() {
        assertEquals("POST /login", HttpSpanNames.format(HttpMethod.POST, "/login"))
    }
}
