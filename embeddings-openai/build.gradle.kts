plugins {
    alias(libs.plugins.kotlin.jvm)
    // kotlinx.serialization builds the OpenAI request body and parses its response without reflection
    // (the compiler synthesizes each `KSerializer`). Pinned to the catalog's Kotlin version so the
    // plugin and standard library agree; declared inline because vendor coordinates stay out of the
    // shared catalog per the module conventions — the same setup `:email-resend`/`:encoding` use.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

// The OpenAI-backed Embedder (platform-go's simplest embeddings backend: one JSON POST). Instead of
// pulling an OpenAI Go/Java SDK, it speaks OpenAI's REST API directly over the repo's `:httpclient-api`
// contract — one `POST https://api.openai.com/v1/embeddings` with a Bearer token — so the adaptation
// (request shape, response→vector parsing, error mapping, Observer instrumentation, circuit breaking)
// is unit-tested through a `FakeHttpClient` with no network, exactly how `:email-resend` exercises its
// vendor path and `:analytics-segment` exercises its captured enqueuer.
//
// `:embeddings-api`, `:httpclient-api`, and `:circuitbreaking` are `api` because `Embedder`,
// `HttpClient`, and `CircuitBreaker` all appear on the public factory surface. `:observability-api`
// and `:errors` are `implementation` details (the Observer is built internally from the
// logger/tracer; the circuit-broken sentinel comes from `:errors` via `:circuitbreaking`), matching
// `:email-resend`. kotlinx-serialization-json is `implementation`: the `@Serializable` payloads are
// internal to this module.
//
// No metrics: platform-go's embedding backends record no metrics either (only the Observer span), so
// there is nothing to defer here beyond what the span already captures.
//
// Seams: the other two vendors platform-go ships — Ollama (`http://localhost:11434/api/embed`,
// `{model,input}` → `{embeddings:[[...]]}`) and Cohere (`https://api.cohere.com/v2/embed`,
// `{texts,model,input_type,embedding_types}` → `{embeddings:{float:[[...]]}}`) — would be wired the
// same way behind `Embedder` over `:httpclient-api`. See the `TODO(<vendor>)` notes in
// `OpenAiEmbedder.kt` and `embeddings-api`'s `EmbeddingProvider`.
dependencies {
    api(project(":embeddings-api"))
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
