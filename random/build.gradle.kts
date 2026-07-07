plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: crypto-secure random strings/bytes backed by java.security.SecureRandom. Depends
// on observability-api because platform-go's random.Generator genuinely instruments every call (an
// Observer.Begin/End span per method, the requested length recorded on both span and log) rather than
// logging incidentally — that behavior is worth carrying over, and the type shows up in
// SecureRandomGenerator's public constructor, so the dependency is `api`, matching how
// observability-api itself re-exposes its own transitive deps.
//
// No DI framework dependency: platform-go's do.go/RegisterGenerator (samber/do) has no Koin analog
// here. `random` sits at the foundation of the dependency graph (per docs/PORTING_STATUS.md, nearly
// everything depends on it), so it stays framework-agnostic; a consumer wires
// `SecureRandomGenerator(logger, tracerProvider)` into its own Koin module (see how
// `observability-koin` does it for the umbrella `Observability`) rather than this module forcing
// koin-core onto every downstream package.
dependencies {
    api(project(":observability-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
