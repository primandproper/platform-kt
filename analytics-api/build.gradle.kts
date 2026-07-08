plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the analytics event-reporting surface (EventReporter), its config, the noop/mock
// doubles, and the multisource composite that fans one event across per-source reporters. Ports
// platform-go's `analytics`, `analytics/noop`, `analytics/mock`, `analytics/config`, and
// `analytics/multisource`.
//
// `observability-api` is an `api` dependency because every public operation opens an Observer span
// (mirroring platform-go, where each reporter method routes through `o11y.Begin(ctx)`), and the
// Observer type appears in the public constructors. `kotlinx-coroutines-core` is `api` because the
// reporter methods are `suspend` (the coroutine-native analog of Go's `ctx`-threaded, error-returning
// methods) — a signature that surfaces in the public interface.
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
