plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the framework-agnostic multi-service server contract and its config. Port of
// platform-go's `server/http` (the `Server` interface, its `Config`, and the static-file handler),
// plus a documented `TODO(grpc-kotlin)` seam standing in for `server/grpc`.
//
// Depends on `:routing-api` because `Server.router()` returns a `routing.Router` (Go's
// `Server.Router() routing.Router`) and the static-file handler is a `routing.HttpHandler`. Depends
// on observability-api because the server is instrumented with an `Observer` (Go threads a
// `logging.Logger` + `tracing.TracerProvider`). Both are `api` — they appear in the public surface.
//
// The Ktor (Netty) implementation lives in `:server-ktor`, mirroring how Go keeps the transport
// (`net/http`) out of the abstraction.
dependencies {
    api(project(":routing-api"))
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
