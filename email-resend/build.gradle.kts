plugins {
    alias(libs.plugins.kotlin.jvm)
    // kotlinx.serialization builds the Resend request body and parses its response without reflection
    // (the compiler synthesizes each `KSerializer`). Pinned to the catalog's Kotlin version so the
    // plugin and standard library agree; declared inline because vendor coordinates stay out of the
    // shared catalog per the module conventions — the same setup `:encoding` uses.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

// The Resend-backed Emailer (platform-go's cleanest email backend). Instead of pulling Resend's Go
// SDK, it speaks Resend's REST API directly over the repo's `:httpclient-api` contract — one JSON
// `POST https://api.resend.com/emails` with a Bearer token — so the adaptation (address formatting,
// request shape, response/error mapping, Observer instrumentation, circuit breaking) is unit-tested
// through a `FakeHttpClient` with no network, exactly how `:analytics-segment` exercises its vendor
// path through a captured enqueuer.
//
// `:email-api`, `:httpclient-api`, and `:circuitbreaking` are `api` because `Emailer`, `HttpClient`,
// and `CircuitBreaker` all appear on the public factory surface. `:observability-api` and `:errors`
// are `implementation` details (the Observer is built internally from the logger/tracer; the API
// exception wraps a failed send), matching `:analytics-segment`. kotlinx-serialization-json is
// `implementation`: the `@Serializable` payloads are internal to this module.
//
// No metrics: platform-go's `resend.Emailer` increments send/error `Int64Counter`s and a latency
// `Float64Histogram`. There is no metrics pillar in platform-kt's observability-api yet, so those are
// a documented `TODO(metrics)` seam (the Observer span already records the operation), exactly as
// `:analytics-segment` and `:circuitbreaking` leave it.
//
// Seams: the other vendors platform-go ships (`sendgrid`, `mailgun`, `mailjet`, `postmark`) would be
// wired the same way behind `Emailer` over `:httpclient-api`; `ses` is an SDK seam. See TODO(<vendor>)
// notes and `email-api`'s EmailProvider.
dependencies {
    api(project(":email-api"))
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
