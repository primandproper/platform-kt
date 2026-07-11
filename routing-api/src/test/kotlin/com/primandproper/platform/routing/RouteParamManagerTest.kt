package com.primandproper.platform.routing

import com.primandproper.platform.observability.NoopLogger
import kotlin.test.Test
import kotlin.test.assertEquals

/** A minimal [RoutingRequest] fake backed by a fixed path-parameter map. */
private class FakeRequest(
    private val params: Map<String, String>,
) : RoutingRequest {
    override val method: HttpMethod = HttpMethod.GET
    override val path: String = "/"

    override fun pathParameter(key: String): String? = params[key]

    override fun header(name: String): String? = null
}

/**
 * Covers the ID/string fetchers from platform-go's `routing/chi/routeparams_test.go`. Here they live
 * in the API module because path-parameter access is abstracted behind [RoutingRequest].
 */
class RouteParamManagerTest {
    private val manager = DefaultRouteParamManager

    @Test
    fun `id fetcher parses a numeric param`() {
        val fetch = manager.buildRouteParamIDFetcher(NoopLogger, "id", "thing")
        assertEquals(123uL, fetch(FakeRequest(mapOf("id" to "123"))))
    }

    @Test
    fun `id fetcher returns zero for a missing param`() {
        val fetch = manager.buildRouteParamIDFetcher(NoopLogger, "id", "thing")
        assertEquals(0uL, fetch(FakeRequest(emptyMap())))
    }

    @Test
    fun `id fetcher returns zero for a non-numeric param`() {
        val fetch = manager.buildRouteParamIDFetcher(NoopLogger, "id", "thing")
        assertEquals(0uL, fetch(FakeRequest(mapOf("id" to "abc"))))
    }

    @Test
    fun `string fetcher returns the raw param`() {
        val fetch = manager.buildRouteParamStringIDFetcher("slug")
        assertEquals("hello", fetch(FakeRequest(mapOf("slug" to "hello"))))
    }

    @Test
    fun `string fetcher returns empty for a missing param`() {
        val fetch = manager.buildRouteParamStringIDFetcher("slug")
        assertEquals("", fetch(FakeRequest(emptyMap())))
    }
}
