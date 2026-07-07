plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the generic cache contract, its config, the noop/mock doubles, and the in-memory
// backend (port of platform-go's `cache`, `cache/noop`, `cache/mock`, and `cache/memory`). Depends on
// observability-api because the in-memory backend genuinely instruments every operation (an
// Observer span per method, the key/length recorded on both pillars), mirroring platform-go's
// `inMemoryCacheImpl`. The `Observer` type shows up in the public constructor, so the dependency is
// `api`, matching how `:random` carries observability-api.
//
// Coroutines are `api`: every cache method suspends (Go threads a `context.Context`; Kotlin suspends
// instead), so `kotlinx.coroutines` types are part of the public surface.
//
// No metrics: platform-go's backends record hit/miss/set/delete counters and a latency histogram
// through a metrics provider. There is no metrics pillar in platform-kt's observability-api yet, so
// those are left as a documented `TODO(metrics)` seam, exactly as `:circuitbreaking` does.
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
