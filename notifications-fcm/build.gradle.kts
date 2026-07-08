plugins {
    alias(libs.plugins.kotlin.jvm)
    // kotlinx.serialization builds the FCM HTTP v1 request body and parses its response/error without
    // reflection (the compiler synthesizes each `KSerializer`). Pinned to the catalog's Kotlin version
    // so plugin and standard library agree; declared inline because vendor coordinates stay out of the
    // shared catalog per the module conventions — the same setup `:email-resend` uses.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

// The FCM-backed PushNotificationSender — the SERVER send side (platform-go's `notifications/mobile/fcm`).
// Instead of pulling Firebase's Go/Java SDK, it speaks FCM's HTTP v1 API directly over the repo's
// `:httpclient-api` contract — one JSON `POST https://fcm.googleapis.com/v1/projects/{id}/messages:send`
// with a `Bearer` token — so the adaptation (request shape, response/error mapping, Observer
// instrumentation, circuit breaking) is unit-tested through a `FakeHttpClient` with no network,
// exactly how `:email-resend` exercises its vendor path.
//
// `:notifications-api`, `:httpclient-api`, and `:circuitbreaking` are `api` because
// `PushNotificationSender`, `HttpClient`, and `CircuitBreaker` all appear on the public factory
// surface. `:observability-api` and `:errors` are `implementation` details. kotlinx-serialization-json
// is `implementation`: the `@Serializable` payloads are internal to this module.
//
// SEAMS: minting the OAuth2 bearer token from a Firebase service-account JSON (sign a JWT, exchange it
// at Google's token endpoint) is a documented `TODO(fcm-oauth)` seam — the sender takes an injected
// token provider so production wires ADC/JWT and tests pass a static token. APNs (the iOS half of
// platform-go's `MultiPlatformPushSender`) is a documented `TODO(apns)` seam; this sender rejects a
// non-Android platform with `PlatformNotSupportedException`.
dependencies {
    api(project(":notifications-api"))
    api(project(":httpclient-api"))
    api(project(":circuitbreaking"))
    implementation(project(":observability-api"))
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
