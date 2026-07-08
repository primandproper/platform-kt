plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Ktor (Netty engine) HTTP server backend. Port of platform-go's `server/http` server impl:
// implements the `:server-api` `Server` contract, mounts a `:routing-ktor` router onto a Ktor
// `Application`, and drives a Netty engine.
//
// Depends on `:routing-ktor` (not just `:routing-api`) because mounting is provider-specific: the
// server downcasts the injected `routing.Router` to the Ktor `KtorRouter` and calls its `install`,
// the seam that replaces Go handing the server a `routing.Router.Handler() http.Handler`. Netty is
// the engine (Go's default net/http server ≈ Ktor+Netty). `ktor-server-test-host` lets the tests
// exercise the application configuration without binding a real port.
//
// New third-party coordinates are declared directly here (not the version catalog) to keep parallel
// module work mergeable, per `:cache-redis`'s precedent.
dependencies {
    api(project(":server-api"))
    api(project(":routing-ktor"))
    api(project(":observability-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")

    testImplementation(project(":observability-testing"))
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
