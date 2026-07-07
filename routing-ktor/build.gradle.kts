plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Ktor routing backend (analog of platform-go's `routing/chi`). Implements the `:routing-api`
// `Router`/`RouteParamManager` contract over Ktor's routing engine and wires the observability
// `Observer` into per-request recovery + logging, mirroring where Go's chi mux instruments.
//
// Ktor's `Routing` plugin lives in `ktor-server-core` (no engine pulled in here — an engine is the
// server's concern, `:server-ktor`). `ktor-server-test-host` gives the tests a call pipeline with no
// bound port, so the wire tests run without touching the network.
//
// New third-party coordinates are declared directly here (not in the version catalog) to keep
// parallel module work mergeable, following `:cache-redis`'s Lettuce precedent.
dependencies {
    api(project(":routing-api"))
    api(project(":observability-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.ktor:ktor-server-core:3.0.3")

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
