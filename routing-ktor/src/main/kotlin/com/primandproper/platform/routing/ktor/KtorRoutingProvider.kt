package com.primandproper.platform.routing.ktor

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.routing.DefaultRouteParamManager
import com.primandproper.platform.routing.RouteParamManager
import com.primandproper.platform.routing.Router
import com.primandproper.platform.routing.RoutingConfig
import com.primandproper.platform.routing.RoutingProvider

/**
 * Builds a [Router] from a [RoutingConfig]. Analog of platform-go's `routingcfg.ProvideRouter`, which
 * switches on `cfg.Provider` and calls `chi.NewRouter`. Here the only provider is
 * [RoutingProvider.KTOR], constructed from an [Observer].
 *
 * Go threads a `logging.Logger` + `tracing.TracerProvider` + `metrics.Provider`; the observability
 * pillars are bundled into one [Observer] here (the metrics provider has no analog yet — see
 * `TODO(metrics)` in [KtorRouter]).
 */
public fun provideRouter(
    config: RoutingConfig,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): Router {
    config.validate()
    return when (config.provider) {
        RoutingProvider.KTOR ->
            KtorRouter(Observer(config.router.serviceName, logger, tracerProvider), config.router)
    }
}

/**
 * Builds a [RouteParamManager] from a [RoutingConfig]. Analog of `routingcfg.ProvideRouteParamManager`.
 * Every provider shares the framework-independent [DefaultRouteParamManager], since path-parameter
 * access is abstracted in `:routing-api`.
 */
public fun provideRouteParamManager(config: RoutingConfig): RouteParamManager =
    when (config.provider) {
        RoutingProvider.KTOR -> DefaultRouteParamManager
    }
