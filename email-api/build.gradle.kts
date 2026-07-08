plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the emailer contract, the outbound-message/branding value types, the provider
// config, the standard email observability keys, and the noop/mock doubles — the port of
// platform-go's `email`, `email/config`, `email/noop`, and `email/mock`. No backend, no vendor SDK,
// no network: a caller (and a unit test) can depend on the `Emailer` seam without any transport on
// the classpath, exactly as `:cache-api` carries the cache contract above `:cache-redis`.
//
// Coroutines are `api`: `Emailer.sendEmail` suspends (Go threads a `context.Context`; Kotlin suspends
// instead), so `kotlinx.coroutines` is part of the public surface.
//
// The real backends live in sibling modules (`:email-resend` here; the remaining vendors are
// documented `TODO(<vendor>)` seams). Per-provider connection config (e.g. `ResendConfig`), the
// Hermes template engine, and the circuit-breaker settings that platform-go's `email/config` unites
// live with those backends, keeping this module transport-free — the same split `:cache-api` uses to
// keep the Redis settings in `:cache-redis`.
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
