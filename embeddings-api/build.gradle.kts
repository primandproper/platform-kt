plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the embedder contract, the input/result value types, the provider config, the
// standard embedding observability keys, and the noop/mock doubles — the port of platform-go's
// `embeddings`, `embeddings/config`, `embeddings/noop`, and `embeddings/mock`. No backend, no vendor
// SDK, no network: a caller (and a unit test) can depend on the `Embedder` seam without any transport
// on the classpath, exactly as `:cache-api` carries the cache contract above `:cache-redis` and
// `:email-api` carries `Emailer` above `:email-resend`.
//
// Coroutines are `api`: `Embedder.generateEmbedding` suspends (Go threads a `context.Context`; Kotlin
// suspends instead), so `kotlinx.coroutines` is part of the public surface.
//
// The real backends live in sibling modules (`:embeddings-openai` here; Ollama and Cohere are
// documented `TODO(<vendor>)` seams). Per-provider connection config (e.g. `OpenAiConfig`) and the
// circuit-breaker settings that platform-go's `embeddings/config` unites live with those backends,
// keeping this module transport-free — the same split `:cache-api` uses to keep the Redis settings in
// `:cache-redis`.
dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
