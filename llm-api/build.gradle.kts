plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the LLM completion contract, the message/role value types, the completion
// params/result value types, the standard LLM observability keys, the provider-selection config,
// and the noop/mock doubles — the port of platform-go's `llm`, `llm/config`, `llm/noop`, and
// `llm/mock`. No backend, no vendor SDK, no network: a caller (and a unit test) can depend on the
// `LlmProvider` seam without any transport on the classpath, exactly as `:email-api` carries the
// emailer contract above `:email-resend` and `:cache-api` carries the cache contract above
// `:cache-redis`.
//
// Coroutines are `api`: `LlmProvider.complete` suspends (Go threads a `context.Context`; Kotlin
// suspends instead), so `kotlinx.coroutines` is part of the public surface.
//
// The real backends live in sibling modules (`:llm-anthropic` here). Go's `llm` package also ships
// an OpenAI backend (`llm/openai`); that is a documented `TODO(openai)` seam — the per-provider
// connection config (API key, base URL, default model) lives with each backend module, keeping this
// module transport-free.
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
