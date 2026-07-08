plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the framework-agnostic routing contract (router, middleware, route-param manager),
// its config, and the mock double. Port of platform-go's `routing` package (router.go,
// context_key.go, routing/config, routing/mock) *minus* the chi implementation, which — like Go's
// `routing/chi` — lives in the provider module (`:routing-ktor`).
//
// Depends on observability-api because the route-param fetchers take a `Logger` (Go threads a
// `logging.Logger`), and the provider config builds an `Observer`. The `Logger` shows up in the
// public `RouteParamManager` surface, so the dependency is `api`.
//
// Coroutines are `api`: an [HttpHandler] is `suspend` (Go's `http.Handler` blocks a goroutine; Kotlin
// suspends instead), so callers write suspend handlers.
dependencies {
    api(project(":observability-api"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
