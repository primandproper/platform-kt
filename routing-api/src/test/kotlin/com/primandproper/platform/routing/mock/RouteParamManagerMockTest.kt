package com.primandproper.platform.routing.mock

import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.routing.HttpMethod
import com.primandproper.platform.routing.RoutingRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeRequest(
    private val params: Map<String, String>,
) : RoutingRequest {
    override val method: HttpMethod = HttpMethod.GET
    override val path: String = "/"

    override fun pathParameter(key: String): String? = params[key]

    override fun header(name: String): String? = null
}

/** Mirrors the moq contract exercised in platform-go's `routing/mock` usage. */
class RouteParamManagerMockTest {
    @Test
    fun `delegates to the configured func and records the call`() {
        val mock =
            RouteParamManagerMock(
                buildRouteParamStringIDFetcherFunc = { key -> { req -> req.pathParameter(key) ?: "" } },
            )

        val fetch = mock.buildRouteParamStringIDFetcher("slug")

        assertEquals("v", fetch(FakeRequest(mapOf("slug" to "v"))))
        assertEquals(listOf("slug"), mock.buildRouteParamStringIDFetcherCalls)
    }

    @Test
    fun `an unmocked method throws`() {
        val mock = RouteParamManagerMock()
        assertFailsWith<IllegalStateException> {
            mock.buildRouteParamIDFetcher(NoopLogger, "id", "thing")
        }
    }
}
