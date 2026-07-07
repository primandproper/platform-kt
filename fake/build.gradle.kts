plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: a reflection-driven test-data generator, port of platform-go's `fake` package.
//
// platform-go leans on two reflection-based faker libraries (brianvoe/gofakeit + go-faker/faker) to
// fill arbitrary structs by walking their fields. The faithful analog here is kotlin-reflect: we walk
// a data class's primary constructor and synthesize a value per parameter from its type (and, like
// go-faker's field-name/tag heuristics, from the parameter *name* — an "email" field gets an
// email-shaped string). `kotlin("reflect")` is pulled in as a DIRECT coordinate at the Kotlin
// plugin's own version; it is an implementation detail and never appears in the public surface.
//
// No datafaker / gofakeit analog dependency: the value shapes we need (words, emails, names, ids,
// primitives, times) are a few lines each, and hand-rolling them keeps the whole generator backed by
// a single seedable `kotlin.random.Random` — which is what makes "fixed seed -> reproducible output"
// trivially true. That determinism is the reason we don't reuse `:random` either: its
// SecureRandomGenerator is intentionally non-seedable (SecureRandom) and carries observability-api,
// neither of which serves a deterministic test-data faker.
dependencies {
    implementation(kotlin("reflect"))

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
