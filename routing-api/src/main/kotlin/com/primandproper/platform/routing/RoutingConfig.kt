package com.primandproper.platform.routing

/**
 * The supported routing providers. Port of Go's `routingcfg.ProviderChi` constant.
 *
 * Go's only provider is `"chi"`. The Kotlin spine is built on Ktor, so the analogous — and only —
 * provider here is [KTOR]. Named, not silently renamed: the wire [value] documents the mapping so a
 * config that once said `chi` in Go maps to `ktor` here.
 */
public enum class RoutingProvider(
    public val value: String,
) {
    KTOR("ktor"),
    ;

    public companion object {
        /** Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if unknown. */
        public fun fromValue(value: String): RoutingProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Provider backend settings. Port of Go's `chi.Config`: the fields configuring the router's
 * observability and CORS behavior. In Go this lives in the `routing/chi` (impl) package and is
 * embedded in `routingcfg.Config` as `*chi.Config`; here the fields are plain, framework-free data
 * (a service name, CORS domains, a logging switch), so — following how `:cache-api` keeps portable
 * settings in the API module while backend-specific config lives with the backend — they sit here and
 * feed the provider factory in `:routing-ktor`.
 *
 * @param serviceName names the router's span/logger scope; required, mirroring `chi.Config`'s
 *   `validation.Required` on `ServiceName`.
 * @param validDomains hostnames CORS accepts credentialed requests from (see `TODO(cors)` in the
 *   Ktor provider).
 * @param enableCORSForLocalhost also accept `localhost` / `127.0.0.1` origins.
 * @param silenceRouteLogging suppress the per-request "response served" log line.
 */
public data class RouterSettings(
    public val serviceName: String,
    public val validDomains: List<String> = emptyList(),
    public val enableCORSForLocalhost: Boolean = false,
    public val silenceRouteLogging: Boolean = false,
) {
    /** Validates the settings, throwing [IllegalArgumentException] on the first problem. Mirrors `ValidateWithContext`. */
    public fun validate() {
        require(serviceName.isNotBlank()) { "routing: serviceName is required" }
    }
}

/**
 * Configures the router. Port of Go's `routingcfg.Config` (`Provider` + nested `*chi.Config`).
 *
 * @param provider which backend to build; the provider factory lives in the backend module
 *   (`:routing-ktor`'s `provideRouter`), matching how Go's `routingcfg.ProvideRouter` dispatches to
 *   `chi.NewRouter`.
 * @param router the backend settings applied to the chosen provider.
 */
public data class RoutingConfig(
    public val provider: RoutingProvider,
    public val router: RouterSettings,
) {
    /** Validates the whole config. Mirrors `routingcfg.Config.ValidateWithContext`. */
    public fun validate() {
        router.validate()
    }
}
