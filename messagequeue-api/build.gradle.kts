plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the publisher/consumer contract, the provider-selection enum + queue-name config,
// the byte-encoding boundary, and the noop/mock doubles (port of platform-go's `messagequeue`, the
// portable parts of `messagequeue/config`, `messagequeue/noop`, and `messagequeue/mock`).
//
// Coroutines are `api`: publishing suspends (Go threads a `context.Context`; Kotlin suspends instead),
// and a consumer delivers messages by driving a handler over a cold `Flow` under the hood, so
// `kotlinx.coroutines` types are part of the public surface.
//
// No observability dependency here: unlike `:cache-api` (whose in-memory backend instruments), the
// noop/mock doubles in this module don't open spans — the instrumented backend lives in
// `:messagequeue-redis`. No metrics either: platform-go's backends record published/consumed/error
// counters and a latency histogram through a metrics provider; there is no metrics pillar in
// platform-kt's observability-api yet, so those are a documented `TODO(metrics)` seam in the backend.
dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
