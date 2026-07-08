package com.primandproper.platform.routing.mock

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.routing.RouteParamManager
import com.primandproper.platform.routing.RoutingRequest

/**
 * A configurable [RouteParamManager] test double, mirroring platform-go's moq-generated
 * `mockrouting.RouteParamManagerMock`. Each method delegates to a settable `...Func`; calling a method
 * whose `Func` was left `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's arguments are recorded in the
 * matching `...Calls` list, standing in for moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = RouteParamManagerMock(
 *     buildRouteParamStringIDFetcherFunc = { key -> { req -> req.pathParameter(key) ?: "" } },
 * )
 * ```
 */
public class RouteParamManagerMock(
    public var buildRouteParamIDFetcherFunc: ((Logger?, String, String) -> (RoutingRequest) -> ULong)? = null,
    public var buildRouteParamStringIDFetcherFunc: ((String) -> (RoutingRequest) -> String)? = null,
) : RouteParamManager {
    /** Records the (logger, key, logDescription) of each [buildRouteParamIDFetcher] call. */
    public val buildRouteParamIDFetcherCalls: MutableList<Triple<Logger?, String, String>> = mutableListOf()

    /** Records the key of each [buildRouteParamStringIDFetcher] call. */
    public val buildRouteParamStringIDFetcherCalls: MutableList<String> = mutableListOf()

    override fun buildRouteParamIDFetcher(
        logger: Logger?,
        key: String,
        logDescription: String,
    ): (RoutingRequest) -> ULong {
        buildRouteParamIDFetcherCalls += Triple(logger, key, logDescription)
        val func =
            buildRouteParamIDFetcherFunc
                ?: error("mock.buildRouteParamIDFetcherFunc: method is null but was just called")
        return func(logger, key, logDescription)
    }

    override fun buildRouteParamStringIDFetcher(key: String): (RoutingRequest) -> String {
        buildRouteParamStringIDFetcherCalls += key
        val func =
            buildRouteParamStringIDFetcherFunc
                ?: error("mock.buildRouteParamStringIDFetcherFunc: method is null but was just called")
        return func(key)
    }
}
