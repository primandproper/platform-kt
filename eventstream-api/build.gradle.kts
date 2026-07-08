plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the framework-agnostic event-streaming contract (port of platform-go's
// `eventstream` root + `eventstream/config` + `eventstream/noop`). It carries the `Event` type, the
// `EventStream` / `BidirectionalEventStream` abstractions, the generic `StreamManager`, the provider
// config, the `EventCodec` used on the wire, and the noop doubles.
//
// The transport lives elsewhere, split by direction: `:eventstream-ktor` is the SERVER that emits
// (SSE/WebSocket endpoints), while `:eventstream-android` is the CLIENT that consumes the same
// streams (OkHttp SSE/WebSocket → `Flow<Event>`). Keeping this module transport-free lets both
// surfaces share the exact same `Event` and `EventCodec`.
//
// Coroutines are `api`: Go threads a `context.Context` and returns channels (`<-chan struct{}`,
// `<-chan *Event`); this port suspends and returns `kotlinx.coroutines` `Job` / `Flow` instead, so
// those types show up in the public surface. `:observability-api` is `api` because `StreamManager`
// takes the standard logger/tracer pair in its public constructor and genuinely instruments every
// method, mirroring the Go `StreamManager`'s `observability.Observer`.
//
// kotlinx-serialization-json is a plain `implementation` (hidden behind `EventCodec`): it is used
// only to build/parse the `{"type":...,"payload":...}` envelope that `conn.WriteJSON(event)` /
// `json.Unmarshal` handle in Go, and only its runtime `JsonElement` API is used, so the serialization
// compiler plugin is not required. It is pulled by direct Maven coordinate to keep parallel module
// work mergeable, per the repo convention for new vendor deps.
dependencies {
    api(project(":observability-api"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":errors"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
