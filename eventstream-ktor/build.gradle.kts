plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. The SERVER that EMITS event streams: SSE and WebSocket endpoints built on Ktor's
// server SSE/WebSocket plugins, the port of platform-go's `eventstream/sse` and
// `eventstream/websocket` upgraders.
//
// The direction is the mirror image of `:eventstream-android`, which CONSUMES the same streams over
// OkHttp. Both sides share the one `Event` and `EventCodec` from `:eventstream-api`; here we frame
// outbound events (SSE `event:`/`data:` lines, or the WebSocket JSON envelope) and, for a
// bidirectional WebSocket, surface inbound frames as a `Flow<Event>`.
//
// Go's upgraders take an `http.ResponseWriter`/`*http.Request` and hand back a stream; Ktor upgrades
// through route builders (`sse { }`, `webSocket { }`) instead, so the port exposes `Route` extensions
// that register an endpoint and hand an `EventStream` to a handler, wiring an `Observer` that spans
// each send exactly as Go's `sse_send` / `ws_send` custom operations do.
//
// Ktor server SSE/WebSocket coordinates are pulled by direct Maven coordinate (not the version
// catalog) to keep parallel module work mergeable, per the repo convention for new vendor deps.
dependencies {
    api(project(":eventstream-api"))
    api(project(":observability-api"))
    implementation(project(":errors"))
    implementation(libs.kotlinx.coroutines.core)

    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-sse:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")

    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation("io.ktor:ktor-client-websockets:3.0.3")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
