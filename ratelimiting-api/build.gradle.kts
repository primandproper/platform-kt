plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the per-key rate limiter contract, its config, the noop/mock doubles, and the
// in-memory token-bucket backend (port of platform-go's `ratelimiting` core + in-memory impl,
// `ratelimiting/noop`, and the portable part of `ratelimiting/config`). Depends on observability-api
// because the in-memory backend instruments every `allow` with an Observer span (recording the key
// and the allow/deny outcome), the platform-kt analog of platform-go's allowed/rejected metric
// counters. The `Observer` type shows up in the public constructor, so the dependency is `api`,
// matching how `:cache-api` carries observability-api.
//
// Coroutines are `api`: `allow` suspends (Go threads a `context.Context`; Kotlin suspends instead),
// so `kotlinx.coroutines` types are part of the public surface.
//
// No metrics: platform-go's limiters record allowed/rejected (and, for Redis, error) counters through
// a metrics provider. There is no metrics pillar in platform-kt's observability-api yet, so those are
// left as a documented `TODO(metrics)` seam, exactly as `:cache-api` / `:circuitbreaking` do.
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
