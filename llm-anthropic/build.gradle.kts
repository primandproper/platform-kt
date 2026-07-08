plugins {
    alias(libs.plugins.kotlin.jvm)
    // kotlinx.serialization builds the Anthropic Messages request body and parses its response without
    // reflection (the compiler synthesizes each `KSerializer`). Pinned to the catalog's Kotlin version
    // so the plugin and standard library agree; declared inline because vendor coordinates stay out of
    // the shared catalog per the module conventions — the same setup `:email-resend`/`:encoding` use.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

// The Anthropic-backed LlmProvider (platform-go's primary LLM backend). Instead of pulling an
// Anthropic SDK, it speaks Anthropic's Messages REST API directly over the repo's `:httpclient-api`
// contract — one JSON `POST https://api.anthropic.com/v1/messages` carrying `x-api-key` and
// `anthropic-version` headers — so the adaptation (request shape, response/error mapping, Observer
// instrumentation, circuit breaking) is unit-tested through a `FakeHttpClient` with no network,
// exactly how `:email-resend` exercises its vendor path and `:analytics-segment` its captured
// enqueuer.
//
// `:llm-api`, `:httpclient-api`, and `:circuitbreaking` are `api` because `LlmProvider`, `HttpClient`,
// and `CircuitBreaker` all appear on the public factory surface. `:observability-api` is an
// `implementation` detail (the Observer is built internally from the logger/tracer), matching
// `:email-resend`. kotlinx-serialization-json is `implementation`: the `@Serializable` payloads are
// internal to this module.
//
// No metrics: platform-go's `anthropic` provider increments request/error `Int64Counter`s and a
// latency `Float64Histogram`. There is no metrics pillar in platform-kt's observability-api yet, so
// those are a documented `TODO(metrics)` seam (the Observer span already records the operation),
// exactly as `:email-resend`/`:analytics-segment`/`:circuitbreaking` leave it.
//
// Seam: platform-go also ships an OpenAI backend (`llm/openai`), wired the same way behind
// `LlmProvider` over `:httpclient-api`. It is a documented `TODO(openai)` seam here, not implemented.
dependencies {
    api(project(":llm-api"))
    api(project(":httpclient-api"))
    api(project(":circuitbreaking"))
    implementation(project(":observability-api"))

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
